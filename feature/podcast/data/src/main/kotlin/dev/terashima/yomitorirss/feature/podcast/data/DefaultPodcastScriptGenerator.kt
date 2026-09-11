package dev.terashima.yomitorirss.feature.podcast.data

import dev.terashima.yomitorirss.core.aiinference.AiTextInference
import dev.terashima.yomitorirss.core.background.LocalAiBackgroundTaskGate
import dev.terashima.yomitorirss.core.background.LocalAiBackgroundTaskPriority
import dev.terashima.yomitorirss.feature.podcast.PodcastGenerationProvider
import dev.terashima.yomitorirss.feature.podcast.PodcastScriptGenerator

class DefaultPodcastScriptGenerator(
  private val localInference: AiTextInference,
  private val cloudInference: AiTextInference,
) : PodcastScriptGenerator {
  override suspend fun generate(provider: PodcastGenerationProvider, prompt: String): String = when (provider) {
    PodcastGenerationProvider.LOCAL -> LocalAiBackgroundTaskGate.withPermit(
      priority = LocalAiBackgroundTaskPriority.NORMAL,
    ) {
      generateWith(localInference, prompt)
    }
    PodcastGenerationProvider.CLOUD -> generateWith(cloudInference, prompt)
  }

  private suspend fun generateWith(inference: AiTextInference, prompt: String): String {
    val model = checkNotNull(inference.selectedModel()) { "利用するAIモデルを選択してください" }
    val promptBudgetChars = minOf(model.promptBudgetChars, model.maxInputChars)
    return inference.generate(limitPodcastPrompt(prompt, promptBudgetChars))
  }
}

internal fun limitPodcastPrompt(prompt: String, maxChars: Int): String {
  require(maxChars > 0) { "maxChars must be positive" }
  if (prompt.length <= maxChars) return prompt

  val inputHeaderMatch = Regex("""(?m)^[ \t]*入力記事:[ \t]*$""").find(prompt)
    ?: return prompt.take(maxChars)
  val inputHeaderLineEnd = prompt.indexOf('\n', inputHeaderMatch.range.last + 1)
  val prefixEnd = if (inputHeaderLineEnd >= 0) inputHeaderLineEnd + 1 else inputHeaderMatch.range.last + 1
  val prefix = prompt.substring(0, prefixEnd)
  val rawArticleBlocks = prompt.substring(prefixEnd)
    .split(Regex("""\n\n(?=[ \t]*---\n(?:記事番号:|タイトル:))"""))
  val articleBlocks = rawArticleBlocks.mapNotNull(::parseArticlePromptBlock)
  if (articleBlocks.isEmpty() || articleBlocks.size != rawArticleBlocks.size) return prompt.take(maxChars)

  val notice = if (articleBlocks.size == 1) {
    "[入力上限に合わせ、記事本文を抜粋しています]\n"
  } else {
    "[入力上限に合わせ、全記事を残したまま各本文を均等に抜粋しています]\n"
  }
  val separator = "\n\n"
  val fixedLength = prefix.length + notice.length +
    articleBlocks.sumOf { it.prefix.length } + separator.length * (articleBlocks.size - 1)
  if (fixedLength > maxChars) return prompt.take(maxChars)

  val bodyBudget = maxChars - fixedLength
  val bodyLimits = distributeBodyBudget(articleBlocks.map { it.body.length }, bodyBudget)
  return buildString(maxChars) {
    append(prefix)
    append(notice)
    articleBlocks.forEachIndexed { index, article ->
      if (index > 0) append(separator)
      append(article.prefix)
      append(article.body.take(bodyLimits[index]))
    }
  }
}

private data class ArticlePromptBlock(
  val prefix: String,
  val body: String,
)

private fun parseArticlePromptBlock(block: String): ArticlePromptBlock? {
  val bodyMarker = "\n本文:\n"
  val bodyMarkerIndex = block.indexOf(bodyMarker)
  if (bodyMarkerIndex < 0) return null
  val bodyStart = bodyMarkerIndex + bodyMarker.length
  return ArticlePromptBlock(
    prefix = block.substring(0, bodyStart),
    body = block.substring(bodyStart),
  )
}

private fun distributeBodyBudget(bodyLengths: List<Int>, budget: Int): List<Int> {
  if (bodyLengths.isEmpty() || budget <= 0) return List(bodyLengths.size) { 0 }

  val limits = IntArray(bodyLengths.size)
  val remaining = bodyLengths.indices.toMutableSet()
  var available = budget
  while (remaining.isNotEmpty() && available > 0) {
    val share = available / remaining.size
    if (share <= 0) {
      remaining.take(available).forEach { limits[it] += 1 }
      break
    }

    val completed = remaining.filter { index -> bodyLengths[index] <= share }
    if (completed.isEmpty()) {
      remaining.forEach { index -> limits[index] = share }
      var remainder = available - share * remaining.size
      remaining.forEach { index ->
        if (remainder <= 0) return@forEach
        limits[index] += 1
        remainder -= 1
      }
      break
    }

    completed.forEach { index ->
      limits[index] = bodyLengths[index]
      available -= bodyLengths[index]
      remaining.remove(index)
    }
  }
  return limits.toList()
}
