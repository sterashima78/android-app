package dev.terashima.yomitorirss.feature.settings.data

import dev.terashima.yomitorirss.core.airuntime.LocalInferenceBackend
import dev.terashima.yomitorirss.core.airuntime.LocalModelManager
import dev.terashima.yomitorirss.feature.settings.AiInferenceBackend
import dev.terashima.yomitorirss.feature.settings.AiInferenceSettings
import dev.terashima.yomitorirss.feature.settings.AiModelDownloadProgress
import dev.terashima.yomitorirss.feature.settings.AiModelRepository
import dev.terashima.yomitorirss.feature.settings.AiModelStatus
import dev.terashima.yomitorirss.feature.settings.AiSummaryProgress
import kotlinx.coroutines.flow.map

class DefaultAiModelRepository(
  private val manager: LocalModelManager,
) : AiModelRepository {
  override val models = manager.models.map { models ->
    models
      .filterNot { model -> model.id in REMOVED_MODEL_IDS }
      .map { model ->
        AiModelStatus(
          id = model.id,
          name = model.name,
          description = model.description,
          source = model.source,
          license = model.license,
          quantization = model.quantization,
          sizeBytes = model.sizeBytes,
          downloadedBytes = model.downloadedBytes,
          downloaded = model.downloaded,
          selected = model.selected,
          recommended = model.recommended,
          memoryLow = model.memoryLow,
          supportsThinking = model.supportsThinking,
        )
      }
  }

  override val downloadProgress = manager.downloadProgress.map { progress ->
    progress?.let {
      AiModelDownloadProgress(
        modelId = it.modelId,
        phase = it.phase,
        downloadedBytes = it.downloadedBytes,
        totalBytes = it.totalBytes,
        estimatedRemainingMillis = it.estimatedRemainingMillis,
      )
    }
  }

  override val summaryProgress = manager.summaryProgress.map { progress ->
    progress?.let {
      AiSummaryProgress(
        stage = it.stage,
        modelName = it.modelName,
        estimatedStageDurationMillis = it.estimatedStageDurationMillis,
      )
    }
  }

  override val summaryPrompt = manager.summaryPrompt
  override val inferenceSettings = manager.inferenceSettings.map { settings ->
    AiInferenceSettings(
      backend = when (settings.backend) {
        LocalInferenceBackend.CPU -> AiInferenceBackend.CPU
        LocalInferenceBackend.GPU -> AiInferenceBackend.GPU
      },
      thinkingEnabled = settings.thinkingEnabled,
    )
  }

  override fun isSupported(): Boolean = manager.isSupported()
  override fun updateSummaryPrompt(prompt: String) = manager.updateSummaryPrompt(prompt)
  override fun resetSummaryPrompt() = manager.resetSummaryPrompt()
  override fun setInferenceBackend(backend: AiInferenceBackend) = manager.setInferenceBackend(
    when (backend) {
      AiInferenceBackend.CPU -> LocalInferenceBackend.CPU
      AiInferenceBackend.GPU -> LocalInferenceBackend.GPU
    },
  )
  override fun setThinkingEnabled(enabled: Boolean) = manager.setThinkingEnabled(enabled)
  override fun downloadModel(modelId: String) = manager.downloadModel(modelId)
  override fun selectModel(modelId: String) = manager.selectModel(modelId)
  override fun deleteModel(modelId: String) = manager.deleteModel(modelId)

  private companion object {
    val REMOVED_MODEL_IDS = setOf(
      "qwen2.5-0.5b-q8",
      "qwen2.5-1.5b-q8",
    )
  }
}
