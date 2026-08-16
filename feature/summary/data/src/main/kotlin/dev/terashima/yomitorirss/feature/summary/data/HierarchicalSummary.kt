package dev.terashima.yomitorirss.feature.summary.data

import dev.terashima.yomitorirss.core.airuntime.LocalModelManager
import dev.terashima.yomitorirss.feature.summary.renderSummaryPrompt
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

const val HIERARCHICAL_SUMMARY_CACHE_VARIANT = "hierarchical-v3-token-budget"

enum class HierarchicalSummaryProgressStage {
  DIRECT,
  CHUNK,
  REDUCTION,
  FINAL,
}

data class HierarchicalSummaryProgress(
  val stage: HierarchicalSummaryProgressStage,
  val current: Int? = null,
  val total: Int? = null,
)

suspend fun LocalModelManager.summarizeHierarchically(
  text: String,
  prompt: String,
  onProgress: (HierarchicalSummaryProgress) -> Unit = {},
): String {
  val model = selectedModel() ?: error("要約モデルをダウンロードして選択してください")
  val contextTokens = model.contextTokens
  val tokenCount: (String) -> Int = { value -> countTokens(value) }
  val normalized = HierarchicalSummaryText.normalize(text)
  if (HierarchicalSummaryBudget.fits(contextTokens, prompt, normalized, tokenCount)) {
    currentCoroutineContext().ensureActive()
    onProgress(HierarchicalSummaryProgress(HierarchicalSummaryProgressStage.DIRECT))
    return summarizeText(normalized, prompt)
  }

  val targetChars = HierarchicalSummaryText.intermediateTargetChars(contextTokens)
  val chunkPlanningPrompt = chunkSummaryPrompt(
    part = CHUNK_PROMPT_PLANNING_INDEX,
    total = CHUNK_PROMPT_PLANNING_INDEX,
    targetChars = targetChars,
  )
  val chunks = HierarchicalSummaryText.split(normalized) { chunk ->
    HierarchicalSummaryBudget.fits(contextTokens, chunkPlanningPrompt, chunk, tokenCount)
  }
  check(chunks.size <= CHUNK_PROMPT_PLANNING_INDEX) { "記事の分割数が上限を超えました" }

  var summaries = chunks.mapIndexed { index, chunk ->
    currentCoroutineContext().ensureActive()
    onProgress(
      HierarchicalSummaryProgress(
        stage = HierarchicalSummaryProgressStage.CHUNK,
        current = index + 1,
        total = chunks.size,
      ),
    )
    summarizeText(
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
  """.trimIndent() + "\n\n"
  val mergePrompt = mergeSummaryPrompt(targetChars)

  var reductionRound = 0
  while (!HierarchicalSummaryBudget.fits(
      contextTokens,
      prompt,
      finalPrefix + HierarchicalSummaryText.join(summaries),
      tokenCount,
    )) {
    check(reductionRound < MAX_REDUCTION_ROUNDS) {
      "長文要約の中間結果を十分に圧縮できませんでした"
    }
    val groups = HierarchicalSummaryText.pack(summaries) { group ->
      HierarchicalSummaryBudget.fits(contextTokens, mergePrompt, group, tokenCount)
    }
    summaries = groups.mapIndexed { index, group ->
      currentCoroutineContext().ensureActive()
      onProgress(
        HierarchicalSummaryProgress(
          stage = HierarchicalSummaryProgressStage.REDUCTION,
          current = index + 1,
          total = groups.size,
        ),
      )
      summarizeText(
        HierarchicalSummaryText.join(group),
        mergePrompt,
      )
    }
    reductionRound += 1
  }

  val finalContext = finalPrefix + HierarchicalSummaryText.join(summaries)
  currentCoroutineContext().ensureActive()
  onProgress(HierarchicalSummaryProgress(HierarchicalSummaryProgressStage.FINAL))
  return summarizeText(finalContext, prompt)
}

fun LocalModelManager.summarizeText(text: String, prompt: String): String {
  val model = selectedModel() ?: error("要約モデルをダウンロードして選択してください")
  val normalized = HierarchicalSummaryText.normalize(text)
  val rendered = renderSummaryPrompt(prompt, normalized)
  check(HierarchicalSummaryBudget.fitsRendered(model.contextTokens, rendered, ::countTokens)) {
    "要約入力がモデルのコンテキスト予算を超えています"
  }
  return cleanSummary(generate(rendered))
}

private fun cleanSummary(value: String): String {
  val result = value
    .substringBefore("<|im_end|>")
    .substringBefore("<end_of_turn>")
    .replace(Regex("<think>.*?</think>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
    .replace(Regex("^要約[:：]?\\s*", RegexOption.IGNORE_CASE), "")
    .trim()
  check(result.isNotBlank()) { "要約結果が空です" }
  return result
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

internal object HierarchicalSummaryBudget {
  private const val OUTPUT_RESERVE_TOKENS = 768
  private const val RUNTIME_RESERVE_TOKENS = 256

  fun fits(
    contextTokens: Int,
    prompt: String,
    article: String,
    tokenCount: (String) -> Int,
  ): Boolean = fitsRendered(contextTokens, renderSummaryPrompt(prompt, article), tokenCount)

  fun fitsRendered(
    contextTokens: Int,
    renderedPrompt: String,
    tokenCount: (String) -> Int,
  ): Boolean {
    require(contextTokens > OUTPUT_RESERVE_TOKENS + RUNTIME_RESERVE_TOKENS) {
      "contextTokens is too small for summary reserves"
    }
    return tokenCount(renderedPrompt).toLong() + OUTPUT_RESERVE_TOKENS + RUNTIME_RESERVE_TOKENS <= contextTokens
  }
}

internal object HierarchicalSummaryText {
  private const val MIN_BREAK_RATIO_PERCENT = 55
  private const val SEPARATOR = "\n\n---\n\n"

  fun intermediateTargetChars(contextTokens: Int): Int =
    ((contextTokens + 1023) / 1024 * 100).coerceIn(240, 600)

  fun normalize(text: String): String = text.replace(Regex("\\s+"), " ").trim()

  fun split(
    text: String,
    fits: (String) -> Boolean,
  ): List<String> {
    val normalized = normalize(text)
    if (normalized.isEmpty()) return emptyList()
    if (fits(normalized)) return listOf(normalized)

    val chunks = mutableListOf<String>()
    var start = 0
    while (start < normalized.length) {
      val remaining = normalized.substring(start).trim()
      if (remaining.isNotEmpty() && fits(remaining)) {
        chunks += remaining
        break
      }

      val hardEnd = maxFittingEnd(normalized, start, fits)
      check(hardEnd > start) { "要約プロンプトがモデルの本文入力予算を使い切っています" }
      val fittingLength = hardEnd - start
      val minimumEnd = start + (fittingLength * MIN_BREAK_RATIO_PERCENT / 100)
      val breakIndex = findBreakIndex(normalized, minimumEnd, hardEnd)
      val end = if (breakIndex >= minimumEnd) breakIndex + 1 else hardEnd
      normalized.substring(start, end).trim().takeIf(String::isNotEmpty)?.let(chunks::add)
      start = end
      while (start < normalized.length && normalized[start].isWhitespace()) start += 1
    }
    return chunks
  }

  fun pack(
    items: List<String>,
    fits: (String) -> Boolean,
  ): List<List<String>> {
    val expanded = items.flatMap { item ->
      val normalized = normalize(item)
      when {
        normalized.isEmpty() -> emptyList()
        fits(normalized) -> listOf(normalized)
        else -> split(normalized, fits)
      }
    }
    if (expanded.isEmpty()) return emptyList()

    val groups = mutableListOf<MutableList<String>>()
    var current = mutableListOf<String>()
    expanded.forEach { item ->
      check(fits(item)) { "中間要約1件が統合プロンプトの入力予算を超えています" }
      val candidate = current + item
      if (current.isNotEmpty() && !fits(join(candidate))) {
        groups += current
        current = mutableListOf()
      }
      current += item
    }
    if (current.isNotEmpty()) groups += current
    return groups
  }

  fun join(items: List<String>): String = items.joinToString(SEPARATOR) { normalize(it) }

  private fun maxFittingEnd(
    text: String,
    start: Int,
    fits: (String) -> Boolean,
  ): Int {
    var lower = start
    var upper = text.length
    while (lower < upper) {
      val middle = lower + (upper - lower + 1) / 2
      val candidate = text.substring(start, middle).trim()
      if (candidate.isNotEmpty() && fits(candidate)) {
        lower = middle
      } else {
        upper = middle - 1
      }
    }
    return lower
  }

  private fun findBreakIndex(text: String, minimumEnd: Int, hardEnd: Int): Int {
    for (index in hardEnd - 1 downTo minimumEnd) {
      if (text[index] in BREAK_CHARACTERS) return index
    }
    return -1
  }

  private val BREAK_CHARACTERS = setOf('。', '！', '？', '.', '!', '?', ';', '；', ' ')
}

private const val CHUNK_PROMPT_PLANNING_INDEX = 999
private const val MAX_REDUCTION_ROUNDS = 8
