package dev.terashima.yomitorirss.core.airuntime

enum class LocalInferenceBackend {
  CPU,
  GPU,
}

enum class LocalPromptFormat {
  CHAT_ML,
  PLAIN,
}

enum class LocalInferenceStage {
  PREPARING_MODEL,
  GENERATING_RESPONSE,
}

data class LocalInferenceSettings(
  val backend: LocalInferenceBackend = LocalInferenceBackend.CPU,
  val thinkingEnabled: Boolean = false,
  val speculativeDecodingEnabled: Boolean = false,
  val contextSizeMode: LocalContextSizeMode = LocalContextSizeMode.AUTO,
)

data class LocalModelStatus(
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
  val contextTokens: Int,
  val maxInputChars: Int,
  val promptBudgetChars: Int,
  val promptFormat: LocalPromptFormat,
)

data class ModelDownloadProgress(
  val modelId: String,
  val phase: String,
  val downloadedBytes: Long,
  val totalBytes: Long,
  val estimatedRemainingMillis: Long? = null,
)

data class LocalInferenceProgress(
  val stage: LocalInferenceStage,
  val modelName: String? = null,
  val estimatedStageDurationMillis: Long? = null,
)

