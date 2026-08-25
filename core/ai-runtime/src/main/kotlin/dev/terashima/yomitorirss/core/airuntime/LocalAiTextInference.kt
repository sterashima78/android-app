package dev.terashima.yomitorirss.core.airuntime

import dev.terashima.yomitorirss.core.aiinference.AiTextInference
import dev.terashima.yomitorirss.core.aiinference.AiTextInferenceModel
import dev.terashima.yomitorirss.core.aiinference.AiTextInferenceProgress
import dev.terashima.yomitorirss.core.aiinference.AiTextInferenceStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class LocalAiTextInference(
  private val manager: LocalModelManager,
) : AiTextInference {
  override val progress: Flow<AiTextInferenceProgress?> = manager.inferenceProgress.map { progress ->
    progress?.toAiTextInferenceProgress()
  }

  override fun selectedModel(): AiTextInferenceModel? {
    val model = manager.selectedModel() ?: return null
    return model.toAiTextInferenceModel(
      cacheIdentity = "local:${model.id}:${manager.inferenceCacheVariant(model.id)}",
    )
  }

  override fun countTokens(text: String): Int = manager.countTokens(text)

  override suspend fun generate(prompt: String): String = withContext(Dispatchers.IO) {
    manager.generate(prompt)
  }
}

internal fun LocalModelStatus.toAiTextInferenceModel(cacheIdentity: String): AiTextInferenceModel =
  AiTextInferenceModel(
    id = id,
    name = name,
    contextTokens = contextTokens,
    maxInputChars = maxInputChars,
    promptBudgetChars = promptBudgetChars,
    cacheIdentity = cacheIdentity,
  )

internal fun LocalInferenceProgress.toAiTextInferenceProgress(): AiTextInferenceProgress =
  AiTextInferenceProgress(
    stage = when (stage) {
      LocalInferenceStage.PREPARING_MODEL -> AiTextInferenceStage.PREPARING_MODEL
      LocalInferenceStage.GENERATING_RESPONSE -> AiTextInferenceStage.GENERATING_RESPONSE
    },
    modelName = modelName,
    estimatedStageDurationMillis = estimatedStageDurationMillis,
  )
