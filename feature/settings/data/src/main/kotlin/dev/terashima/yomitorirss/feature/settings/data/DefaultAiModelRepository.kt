package dev.terashima.yomitorirss.feature.settings.data

import dev.terashima.yomitorirss.core.airuntime.LocalModelManager
import dev.terashima.yomitorirss.feature.settings.AiModelDownloadProgress
import dev.terashima.yomitorirss.feature.settings.AiModelRepository
import dev.terashima.yomitorirss.feature.settings.AiModelStatus
import dev.terashima.yomitorirss.feature.settings.AiSummaryProgress
import kotlinx.coroutines.flow.map

class DefaultAiModelRepository(
  private val manager: LocalModelManager,
) : AiModelRepository {
  override val models = manager.models.map { models ->
    models.map { model ->
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

  override fun isSupported(): Boolean = manager.isSupported()
  override fun updateSummaryPrompt(prompt: String) = manager.updateSummaryPrompt(prompt)
  override fun resetSummaryPrompt() = manager.resetSummaryPrompt()
  override fun downloadModel(modelId: String) = manager.downloadModel(modelId)
  override fun selectModel(modelId: String) = manager.selectModel(modelId)
  override fun deleteModel(modelId: String) = manager.deleteModel(modelId)
}
