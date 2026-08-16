package dev.terashima.yomitorirss.feature.settings.data

import android.content.Context
import dev.terashima.yomitorirss.core.airuntime.LocalInferenceBackend
import dev.terashima.yomitorirss.core.airuntime.LocalInferenceStage
import dev.terashima.yomitorirss.core.airuntime.LocalModelBenchmarkComparison
import dev.terashima.yomitorirss.core.airuntime.LocalModelBenchmarkRunner
import dev.terashima.yomitorirss.core.airuntime.LocalModelBenchmarkSample
import dev.terashima.yomitorirss.core.airuntime.LocalModelManager
import dev.terashima.yomitorirss.core.background.LocalAiBackgroundTaskGate
import dev.terashima.yomitorirss.core.background.LocalAiBackgroundTaskPriority
import dev.terashima.yomitorirss.feature.settings.AiInferenceBackend
import dev.terashima.yomitorirss.feature.settings.AiInferenceSettings
import dev.terashima.yomitorirss.feature.settings.AiModelBenchmarkComparison
import dev.terashima.yomitorirss.feature.settings.AiModelBenchmarkSample
import dev.terashima.yomitorirss.feature.settings.AiModelRepository
import dev.terashima.yomitorirss.feature.settings.AiModelStatus
import dev.terashima.yomitorirss.feature.settings.AiSummaryProgress
import dev.terashima.yomitorirss.feature.summary.data.SummaryPromptStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

class DefaultAiModelRepository(
  context: Context,
  private val manager: LocalModelManager,
) : AiModelRepository {
  private val downloadStateStore = AiModelDownloadStateStore(context)
  private val downloadScheduler = AiModelDownloadScheduler(context, downloadStateStore)
  private val summaryPromptStore = SummaryPromptStore(context)
  private val benchmarkRunner = LocalModelBenchmarkRunner(context, manager)

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
        supportsThinking = model.supportsThinking,
        supportsSpeculativeDecoding = model.supportsSpeculativeDecoding,
      )
    }
  }

  override val downloadProgress = downloadStateStore.progress.onEach { progress ->
    if (progress?.phase == "completed") manager.refreshModels()
  }

  override val summaryProgress = manager.inferenceProgress.map { progress ->
    progress?.let {
      AiSummaryProgress(
        stage = when (it.stage) {
          LocalInferenceStage.PREPARING_MODEL -> "preparing_model"
          LocalInferenceStage.GENERATING_RESPONSE -> "generating_summary"
        },
        modelName = it.modelName,
        estimatedStageDurationMillis = it.estimatedStageDurationMillis,
      )
    }
  }

  override val summaryPrompt = summaryPromptStore.prompt
  override val inferenceSettings = manager.inferenceSettings.map { settings ->
    AiInferenceSettings(
      backend = settings.backend.toDomain(),
      thinkingEnabled = settings.thinkingEnabled,
      speculativeDecodingEnabled = settings.speculativeDecodingEnabled,
    )
  }

  override fun isSupported(): Boolean = manager.isSupported()
  override fun updateSummaryPrompt(prompt: String) = summaryPromptStore.update(prompt)
  override fun resetSummaryPrompt() = summaryPromptStore.reset()
  override fun setInferenceBackend(backend: AiInferenceBackend) = manager.setInferenceBackend(
    when (backend) {
      AiInferenceBackend.CPU -> LocalInferenceBackend.CPU
      AiInferenceBackend.GPU -> LocalInferenceBackend.GPU
    },
  )
  override fun setThinkingEnabled(enabled: Boolean) = manager.setThinkingEnabled(enabled)
  override fun setSpeculativeDecodingEnabled(enabled: Boolean) = manager.setSpeculativeDecodingEnabled(enabled)

  override suspend fun benchmarkSelectedModel(): AiModelBenchmarkComparison =
    LocalAiBackgroundTaskGate.withPermit(LocalAiBackgroundTaskPriority.HIGH) {
      withContext(Dispatchers.IO) {
        benchmarkRunner.runSelectedModelComparison().toDomain()
      }
    }

  override fun downloadModel(modelId: String) {
    val model = manager.models.value.firstOrNull { it.id == modelId }
      ?: error("AIモデルが見つかりません")
    if (model.downloaded) {
      manager.refreshModels()
      return
    }
    downloadScheduler.schedule(model.id, model.sizeBytes)
  }

  override fun selectModel(modelId: String) = manager.selectModel(modelId)
  override fun deleteModel(modelId: String) = manager.deleteModel(modelId)
}

private fun LocalInferenceBackend.toDomain(): AiInferenceBackend = when (this) {
  LocalInferenceBackend.CPU -> AiInferenceBackend.CPU
  LocalInferenceBackend.GPU -> AiInferenceBackend.GPU
}

private fun LocalModelBenchmarkComparison.toDomain() = AiModelBenchmarkComparison(
  modelName = modelName,
  backend = backend.toDomain(),
  requestedPrefillTokens = requestedPrefillTokens,
  requestedDecodeTokens = requestedDecodeTokens,
  standard = standard.toDomain(),
  speculative = speculative?.toDomain(),
  speculativeError = speculativeError,
)

private fun LocalModelBenchmarkSample.toDomain() = AiModelBenchmarkSample(
  speculativeDecodingEnabled = speculativeDecodingEnabled,
  initTimeMillis = initTimeMillis,
  timeToFirstTokenMillis = timeToFirstTokenMillis,
  prefillTokenCount = prefillTokenCount,
  decodeTokenCount = decodeTokenCount,
  prefillTokensPerSecond = prefillTokensPerSecond,
  decodeTokensPerSecond = decodeTokensPerSecond,
  totalTimeMillis = totalTimeMillis,
)
