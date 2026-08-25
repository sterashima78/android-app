package dev.terashima.yomitorirss.feature.summary

data class SummaryCloudGenerationResult(
  val modelId: String,
  val text: String,
)

interface SummaryCloudInference {
  fun isAvailable(): Boolean
  fun selectedModelId(): String?
  suspend fun generate(prompt: String): SummaryCloudGenerationResult
  suspend fun generateFromUrl(url: String, prompt: String): SummaryCloudGenerationResult
}
