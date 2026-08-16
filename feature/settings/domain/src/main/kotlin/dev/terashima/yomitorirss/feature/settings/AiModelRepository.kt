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

data class AiModelBenchmarkSample(
  val speculativeDecodingEnabled: Boolean,
  val initTimeMillis: Long,
  val timeToFirstTokenMillis: Long,
  val prefillTokenCount: Int,
  val decodeTokenCount: Int,
  val prefillTokensPerSecond: Double,
  val decodeTokensPerSecond: Double,
  val totalTimeMillis: Long,
)

data class AiModelBenchmarkComparison(
  val modelName: String,
  val backend: AiInferenceBackend,
  val requestedPrefillTokens: Int,
  val requestedDecodeTokens: Int,
  val standard: AiModelBenchmarkSample,
  val speculative: AiModelBenchmarkSample?,
  val speculativeError: String? = null,
) {
  val decodeSpeedup: Double?
    get() = speculative
      ?.decodeTokensPerSecond
      ?.takeIf { standard.decodeTokensPerSecond > 0 }
      ?.div(standard.decodeTokensPerSecond)

  val totalTimeSpeedup: Double?
    get() = speculative
      ?.totalTimeMillis
      ?.takeIf { it > 0 }
      ?.let { standard.totalTimeMillis.toDouble() / it }
}

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
  suspend fun benchmarkSelectedModel(): AiModelBenchmarkComparison
  fun downloadModel(modelId: String)
  fun selectModel(modelId: String)
  fun deleteModel(modelId: String)
}
