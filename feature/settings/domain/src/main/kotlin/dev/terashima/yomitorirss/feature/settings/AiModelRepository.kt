package dev.terashima.yomitorirss.feature.settings

import kotlinx.coroutines.flow.Flow

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
)

data class AiModelDownloadProgress(
  val modelId: String,
  val phase: String,
  val downloadedBytes: Long,
  val totalBytes: Long,
  val estimatedRemainingMillis: Long? = null,
)

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

  fun isSupported(): Boolean
  fun updateSummaryPrompt(prompt: String)
  fun resetSummaryPrompt()
  fun downloadModel(modelId: String)
  fun selectModel(modelId: String)
  fun deleteModel(modelId: String)
}
