package dev.terashima.yomitorirss

import dev.terashima.yomitorirss.core.aicloudopenai.ChatGptModelPreferences
import dev.terashima.yomitorirss.core.aicloudopenai.ChatGptOpenAiClient
import dev.terashima.yomitorirss.feature.summary.SummaryCloudGenerationResult
import dev.terashima.yomitorirss.feature.summary.SummaryCloudInference

internal class ChatGptSummaryCloudInference(
  private val client: ChatGptOpenAiClient,
  private val modelPreferences: ChatGptModelPreferences,
) : SummaryCloudInference {
  override fun isAvailable(): Boolean =
    client.connectionStatus().connected && modelPreferences.selectedModelId() != null

  override fun selectedModelId(): String? = modelPreferences.selectedModelId()

  override suspend fun generate(prompt: String): SummaryCloudGenerationResult {
    val modelId = requireSelectedModel()
    val result = client.generate(modelId, prompt)
    return SummaryCloudGenerationResult(result.modelId, result.text)
  }

  override suspend fun generateFromUrl(url: String, prompt: String): SummaryCloudGenerationResult {
    val modelId = requireSelectedModel()
    val result = client.generateWithWebSearch(modelId, prompt, url)
    return SummaryCloudGenerationResult(result.modelId, result.text)
  }

  private fun requireSelectedModel(): String = modelPreferences.selectedModelId()
    ?: error("ChatGPT / Codex の利用モデルを選択してください")
}
