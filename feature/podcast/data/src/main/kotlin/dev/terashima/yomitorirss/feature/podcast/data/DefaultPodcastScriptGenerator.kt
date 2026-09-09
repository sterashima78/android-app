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
    checkNotNull(inference.selectedModel()) { "利用するAIモデルを選択してください" }
    return inference.generate(prompt)
  }
}
