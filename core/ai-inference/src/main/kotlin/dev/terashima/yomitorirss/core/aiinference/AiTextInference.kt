package dev.terashima.yomitorirss.core.aiinference

import kotlinx.coroutines.flow.Flow

enum class AiTextInferenceStage {
  PREPARING_MODEL,
  GENERATING_RESPONSE,
}

data class AiTextInferenceProgress(
  val stage: AiTextInferenceStage,
  val modelName: String? = null,
  val estimatedStageDurationMillis: Long? = null,
)

data class AiTextInferenceModel(
  val id: String,
  val name: String,
  val contextTokens: Int,
  val maxInputChars: Int,
  val promptBudgetChars: Int,
  val cacheVariant: String,
) {
  init {
    require(id.isNotBlank()) { "AI model id must not be blank" }
    require(name.isNotBlank()) { "AI model name must not be blank" }
    require(contextTokens > 0) { "AI model contextTokens must be positive" }
    require(maxInputChars > 0) { "AI model maxInputChars must be positive" }
    require(promptBudgetChars > 0) { "AI model promptBudgetChars must be positive" }
    require(cacheVariant.isNotBlank()) { "AI model cacheVariant must not be blank" }
  }
}

interface AiTextInference {
  val progress: Flow<AiTextInferenceProgress?>

  fun selectedModel(): AiTextInferenceModel?

  fun countTokens(text: String): Int

  suspend fun generate(prompt: String): String
}
