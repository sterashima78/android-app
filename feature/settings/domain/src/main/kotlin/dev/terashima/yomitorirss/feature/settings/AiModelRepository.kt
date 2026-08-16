package dev.terashima.yomitorirss.feature.settings

import kotlinx.coroutines.flow.Flow

enum class AiInferenceBackend {
  CPU,
  GPU,
}

enum class AiContextSizeMode(val tokens: Int?) {
  AUTO(null),
  CONTEXT_4K(4_096),
  CONTEXT_8K(8_192),
  CONTEXT_16K(16_384),
  CONTEXT_32K(32_768),
}

data class AiInferenceSettings(
  val backend: AiInferenceBackend = AiInferenceBackend.CPU,
  val thinkingEnabled: Boolean = false,
  val speculativeDecodingEnabled: Boolean = false,
  val contextSizeMode: AiContextSizeMode = AiContextSizeMode.AUTO,
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
  val contextTokens: Int = 8_192,
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

data class AiContextBenchmarkSample(
  val contextTokens: Int,
  val requestedPrefillTokens: Int,
  val initTimeMillis: Long,
  val inferenceTimeMillis: Long,
  val baselinePssBytes: Long,
  val peakPssBytes: Long,
  val peakNativePssBytes: Long,
  val peakGraphicsPssBytes: Long,
  val minimumAvailableMemoryBytes: Long,
  val safe: Boolean,
  val error: String? = null,
) {
  val succeeded: Boolean
    get() = error == null
}

data class AiContextBenchmarkReport(
  val modelName: String,
  val backend: AiInferenceBackend,
  val speculativeDecodingEnabled: Boolean,
  val totalDeviceMemoryBytes: Long,
  val recommendedContextTokens: Int,
  val measuredAtEpochMillis: Long,
  val samples: List<AiContextBenchmarkSample>,
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
  fun setContextSizeMode(mode: AiContextSizeMode)
  suspend fun benchmarkSelectedModel(): AiModelBenchmarkComparison
  suspend fun benchmarkSelectedModelContexts(): AiContextBenchmarkReport
  fun lastContextBenchmark(): AiContextBenchmarkReport?
  fun downloadModel(modelId: String)
  fun selectModel(modelId: String)
  fun deleteModel(modelId: String)
}
