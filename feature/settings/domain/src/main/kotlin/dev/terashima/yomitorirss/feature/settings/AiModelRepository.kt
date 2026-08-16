package dev.terashima.yomitorirss.feature.settings

import kotlinx.coroutines.flow.Flow

enum class AiInferenceBackend {
  CPU,
  GPU,
}

data class AiInferenceSettings(
  val backend: AiInferenceBackend = AiInferenceBackend.CPU,
  val thinkingEnabled: Boolean = false,
  val speculativeDecodingEnabled: Boolean = false,
)

data class AiModelStatus(
  val id: String,
  val name: String,
  val description: String,
  val source: String,
  val license: String,
  val quantization: String,
  val sizeBytes: Long,
  val downloadedBytes: Long,
  val downloaded: Boolean,
  val selected: Boolean,
  val recommended: Boolean,
  val memoryLow: Boolean,
  val supportsThinking: Boolean,
  val supportsSpeculativeDecoding: Boolean,
)

data class AiModelDownloadProgress(
  val modelId: String,
  val phase: String,
  val downloadedBytes: Long,
  val totalBytes: Long,
  val estimatedRemainingMillis: Long? = null,
) {
  val isActive: Boolean
    get() = phase == "queued" || phase == "downloading" || phase == "verifying"
}

data class AiSummaryProgress(
  val stage: String,
  val modelName: String? = null,
  val estimatedStageDurationMillis: Long? = null,
)

interface AiModelRepository {
  val models: Flow<List<AiModelStatus>>
  val downloadProgress: Flow<AiModelDownloadProgress?>
  val summaryProgress: Flow<AiSummaryProgress?>
  val summaryPrompt: Flow<String>
  val inferenceSettings: Flow<AiInferenceSettings>

  fun isSupported(): Boolean
  fun updateSummaryPrompt(prompt: String)
  fun resetSummaryPrompt()
  fun setInferenceBackend(backend: AiInferenceBackend)
  fun setThinkingEnabled(enabled: Boolean)
  fun setSpeculativeDecodingEnabled(enabled: Boolean)
  fun downloadModel(modelId: String)
  fun selectModel(modelId: String)
  fun deleteModel(modelId: String)
}
