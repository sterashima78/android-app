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

  val marker = "\n\n[入力記事はモデルの入力上限に合わせて末尾を省略しています]"
  if (marker.length >= maxChars) return prompt.take(maxChars)
  return prompt.take(maxChars - marker.length).trimEnd() + marker
}
