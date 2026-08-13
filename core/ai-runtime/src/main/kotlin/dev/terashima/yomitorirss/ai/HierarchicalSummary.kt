package dev.terashima.yomitorirss.core.airuntime

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

const val HIERARCHICAL_SUMMARY_CACHE_VARIANT = "hierarchical-v1"

suspend fun LocalModelManager.summarizeHierarchically(
  text: String,
  prompt: String = summaryPrompt.value,
): String {
  val model = selectedModel() ?: error("要約モデルをダウンロードして選択してください")
  val maxInputChars = HierarchicalSummaryText.inputLimitFor(model.id)
  val normalized = HierarchicalSummaryText.normalize(text)
  if (normalized.length <= maxInputChars) {
    currentCoroutineContext().ensureActive()
    return summarize(normalized, prompt)
  }

  val chunks = HierarchicalSummaryText.split(normalized, maxInputChars)
  val targetChars = HierarchicalSummaryText.intermediateTargetChars(maxInputChars)
  var summaries = chunks.mapIndexed { index, chunk ->
    currentCoroutineContext().ensureActive()
    summarize(
      chunk,
      chunkSummaryPrompt(
        part = index + 1,
        total = chunks.size,
        targetChars = targetChars,
      ),
    )
  }

  val finalPrefix = """
    以下は、長い記事を先頭から順に分割して作成した中間要約です。
    すべて同じ記事の一部なので、全体を1つの記事として扱ってください。

  """.trimIndent()
  val finalBudget = (maxInputChars - finalPrefix.length).coerceAtLeast(maxInputChars / 2)

  var reductionRound = 0
  while (HierarchicalSummaryText.join(summaries).length > finalBudget) {
    check(reductionRound < MAX_REDUCTION_ROUNDS) {
      "長文要約の中間結果を十分に圧縮できませんでした"
    }
    val groups = HierarchicalSummaryText.pack(summaries, maxInputChars)
    summaries = groups.map { group ->
      currentCoroutineContext().ensureActive()
      summarize(
        HierarchicalSummaryText.join(group),
        mergeSummaryPrompt(targetChars),
      )
    }
    reductionRound += 1
  }

  val finalContext = finalPrefix + HierarchicalSummaryText.join(summaries)
  currentCoroutineContext().ensureActive()
  return summarize(finalContext, prompt)
}

private fun chunkSummaryPrompt(
  part: Int,
  total: Int,
  targetChars: Int,
): String = """
  次は長い記事を分割した $part/$total 番目の本文です。
  記事全体の最終要約を後で作るため、この部分の重要情報を日本語で最大${targetChars}文字程度に圧縮してください。
  - 固有名詞、数値、日時、主張、結論、因果関係を優先する
  - 後続の部分を読まないと分からないことを推測しない
  - 重要な情報を前半や末尾だけに偏らせず拾う
  - 本文にない情報を加えない
  - 前置きは付けない

  記事本文:
  {{article}}
""".trimIndent()

private fun mergeSummaryPrompt(targetChars: Int): String = """
  以下は同じ記事の連続した部分から作成した中間要約です。
  後で記事全体を要約できるよう、情報を失わない範囲で重複を統合し、日本語で最大${targetChars}文字程度に圧縮してください。
  - 固有名詞、数値、日時、主張、結論、因果関係を優先する
  - 各部分にしか存在しない重要事項を落とさない
  - 本文にない情報を加えない
  - 前置きは付けない

  中間要約:
  {{article}}
""".trimIndent()

internal object HierarchicalSummaryText {
  private const val DEFAULT_INPUT_LIMIT = 700
  private const val MIN_BREAK_RATIO_PERCENT = 55
  private const val SEPARATOR = "\n\n---\n\n"

  fun inputLimitFor(modelId: String): Int = when (modelId) {
    "qwen2.5-0.5b-q8", "qwen2.5-1.5b-q8" -> 700
    "qwen3-4b-mixed-int4" -> 1_200
    "gemma4-e2b-it", "gemma4-e4b-it" -> 2_500
    else -> DEFAULT_INPUT_LIMIT
  }

  fun intermediateTargetChars(maxInputChars: Int): Int =
    (maxInputChars / 3).coerceIn(180, 400)

  fun normalize(text: String): String = text.replace(Regex("\\s+"), " ").trim()

  fun split(text: String, maxChars: Int): List<String> {
    require(maxChars > 0) { "maxChars must be positive" }
    val normalized = normalize(text)
    if (normalized.isEmpty()) return emptyList()
    if (normalized.length <= maxChars) return listOf(normalized)

    val chunks = mutableListOf<String>()
    var start = 0
    while (start < normalized.length) {
      val hardEnd = (start + maxChars).coerceAtMost(normalized.length)
      if (hardEnd == normalized.length) {
        normalized.substring(start).trim().takeIf(String::isNotEmpty)?.let(chunks::add)
        break
      }

      val minimumEnd = start + (maxChars * MIN_BREAK_RATIO_PERCENT / 100)
      val breakIndex = findBreakIndex(normalized, minimumEnd, hardEnd)
      val end = if (breakIndex >= minimumEnd) breakIndex + 1 else hardEnd
      normalized.substring(start, end).trim().takeIf(String::isNotEmpty)?.let(chunks::add)
      start = end
      while (start < normalized.length && normalized[start].isWhitespace()) start += 1
    }
    return chunks
  }

  fun pack(items: List<String>, maxChars: Int): List<List<String>> {
    require(maxChars > 0) { "maxChars must be positive" }
    val expanded = items.flatMap { item ->
      val normalized = normalize(item)
      if (normalized.length <= maxChars) listOf(normalized) else split(normalized, maxChars)
    }.filter(String::isNotEmpty)
    if (expanded.isEmpty()) return emptyList()

    val groups = mutableListOf<MutableList<String>>()
    var current = mutableListOf<String>()
    var currentLength = 0
    expanded.forEach { item ->
      val addedLength = item.length + if (current.isEmpty()) 0 else SEPARATOR.length
      if (current.isNotEmpty() && currentLength + addedLength > maxChars) {
        groups += current
        current = mutableListOf()
        currentLength = 0
      }
      current += item
      currentLength += item.length + if (current.size == 1) 0 else SEPARATOR.length
    }
    if (current.isNotEmpty()) groups += current
    return groups
  }

  fun join(items: List<String>): String = items.joinToString(SEPARATOR) { normalize(it) }

  private fun findBreakIndex(text: String, minimumEnd: Int, hardEnd: Int): Int {
    for (index in hardEnd - 1 downTo minimumEnd) {
      if (text[index] in BREAK_CHARACTERS) return index
    }
    return -1
  }

  private val BREAK_CHARACTERS = setOf('。', '！', '？', '.', '!', '?', ';', '；', ' ')
}

private const val MAX_REDUCTION_ROUNDS = 8
