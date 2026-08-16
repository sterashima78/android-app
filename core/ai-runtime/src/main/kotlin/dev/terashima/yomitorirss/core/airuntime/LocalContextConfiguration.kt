package dev.terashima.yomitorirss.core.airuntime

import android.content.Context

enum class LocalContextSizeMode(val fixedTokens: Int?) {
  AUTO(null),
  CONTEXT_4K(4_096),
  CONTEXT_8K(8_192),
  CONTEXT_16K(16_384),
  CONTEXT_32K(32_768),
}

data class LocalContextBenchmarkSample(
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

data class LocalContextBenchmarkReport(
  val modelId: String,
  val modelName: String,
  val backend: LocalInferenceBackend,
  val speculativeDecodingEnabled: Boolean,
  val totalDeviceMemoryBytes: Long,
  val recommendedContextTokens: Int,
  val measuredAtEpochMillis: Long,
  val samples: List<LocalContextBenchmarkSample>,
)

internal fun resolveContextTokens(
  mode: LocalContextSizeMode,
  maxSupportedContextTokens: Int,
  benchmarkRecommendation: Int?,
): Int {
  val requested = mode.fixedTokens ?: benchmarkRecommendation ?: DEFAULT_CONTEXT_TOKENS
  return requested.coerceIn(MIN_CONTEXT_TOKENS, maxSupportedContextTokens)
}

internal fun chooseRecommendedContextTokens(
  samples: List<LocalContextBenchmarkSample>,
  fallbackTokens: Int = DEFAULT_CONTEXT_TOKENS,
): Int {
  samples.filter { it.succeeded && it.safe }
    .maxByOrNull(LocalContextBenchmarkSample::contextTokens)
    ?.let { return it.contextTokens }

  val successful = samples.filter(LocalContextBenchmarkSample::succeeded)
  return successful
    .filter { it.contextTokens <= fallbackTokens }
    .maxByOrNull(LocalContextBenchmarkSample::contextTokens)
    ?.contextTokens
    ?: successful.minByOrNull(LocalContextBenchmarkSample::contextTokens)?.contextTokens
    ?: fallbackTokens
}

internal class LocalContextBenchmarkStore(context: Context) {
  private val preferences = context.applicationContext.getSharedPreferences(
    PREFERENCES_NAME,
    Context.MODE_PRIVATE,
  )

  fun recommendedContextTokens(
    modelId: String,
    backend: LocalInferenceBackend,
    speculativeDecodingEnabled: Boolean,
  ): Int? = preferences.getInt(
    key(modelId, backend, speculativeDecodingEnabled, "recommended"),
    0,
  ).takeIf { it > 0 }

  fun save(report: LocalContextBenchmarkReport) {
    val prefix = prefix(report.modelId, report.backend, report.speculativeDecodingEnabled)
    val editor = preferences.edit()
      .putInt("$prefix.recommended", report.recommendedContextTokens)
      .putLong("$prefix.measured_at", report.measuredAtEpochMillis)
      .putLong("$prefix.total_memory", report.totalDeviceMemoryBytes)
      .putString("$prefix.model_name", report.modelName)

    SUPPORTED_CONTEXT_TOKENS.forEach { contextTokens ->
      val samplePrefix = "$prefix.sample.$contextTokens"
      val sample = report.samples.firstOrNull { it.contextTokens == contextTokens }
      if (sample == null) {
        SAMPLE_FIELDS.forEach { field -> editor.remove("$samplePrefix.$field") }
      } else {
        editor
          .putInt("$samplePrefix.prefill", sample.requestedPrefillTokens)
          .putLong("$samplePrefix.init_ms", sample.initTimeMillis)
          .putLong("$samplePrefix.inference_ms", sample.inferenceTimeMillis)
          .putLong("$samplePrefix.baseline_pss", sample.baselinePssBytes)
          .putLong("$samplePrefix.peak_pss", sample.peakPssBytes)
          .putLong("$samplePrefix.peak_native_pss", sample.peakNativePssBytes)
          .putLong("$samplePrefix.peak_graphics_pss", sample.peakGraphicsPssBytes)
          .putLong("$samplePrefix.min_available", sample.minimumAvailableMemoryBytes)
          .putBoolean("$samplePrefix.safe", sample.safe)
          .putString("$samplePrefix.error", sample.error)
      }
    }
    editor.apply()
  }

  fun load(
    modelId: String,
    modelName: String,
    backend: LocalInferenceBackend,
    speculativeDecodingEnabled: Boolean,
  ): LocalContextBenchmarkReport? {
    val prefix = prefix(modelId, backend, speculativeDecodingEnabled)
    val recommended = preferences.getInt("$prefix.recommended", 0).takeIf { it > 0 } ?: return null
    val samples = SUPPORTED_CONTEXT_TOKENS.mapNotNull { contextTokens ->
      val samplePrefix = "$prefix.sample.$contextTokens"
      if (!preferences.contains("$samplePrefix.prefill")) return@mapNotNull null
      LocalContextBenchmarkSample(
        contextTokens = contextTokens,
        requestedPrefillTokens = preferences.getInt("$samplePrefix.prefill", 0),
        initTimeMillis = preferences.getLong("$samplePrefix.init_ms", 0),
        inferenceTimeMillis = preferences.getLong("$samplePrefix.inference_ms", 0),
        baselinePssBytes = preferences.getLong("$samplePrefix.baseline_pss", 0),
        peakPssBytes = preferences.getLong("$samplePrefix.peak_pss", 0),
        peakNativePssBytes = preferences.getLong("$samplePrefix.peak_native_pss", 0),
        peakGraphicsPssBytes = preferences.getLong("$samplePrefix.peak_graphics_pss", 0),
        minimumAvailableMemoryBytes = preferences.getLong("$samplePrefix.min_available", 0),
        safe = preferences.getBoolean("$samplePrefix.safe", false),
        error = preferences.getString("$samplePrefix.error", null),
      )
    }
    return LocalContextBenchmarkReport(
      modelId = modelId,
      modelName = preferences.getString("$prefix.model_name", null) ?: modelName,
      backend = backend,
      speculativeDecodingEnabled = speculativeDecodingEnabled,
      totalDeviceMemoryBytes = preferences.getLong("$prefix.total_memory", 0),
      recommendedContextTokens = recommended,
      measuredAtEpochMillis = preferences.getLong("$prefix.measured_at", 0),
      samples = samples,
    )
  }

  private fun key(
    modelId: String,
    backend: LocalInferenceBackend,
    speculativeDecodingEnabled: Boolean,
    suffix: String,
  ): String = "${prefix(modelId, backend, speculativeDecodingEnabled)}.$suffix"

  private fun prefix(
    modelId: String,
    backend: LocalInferenceBackend,
    speculativeDecodingEnabled: Boolean,
  ): String = "benchmark.$modelId.${backend.name.lowercase()}.${if (speculativeDecodingEnabled) "speculative" else "standard"}"

  companion object {
    private const val PREFERENCES_NAME = "local_context_benchmarks"
    private val SAMPLE_FIELDS = listOf(
      "prefill",
      "init_ms",
      "inference_ms",
      "baseline_pss",
      "peak_pss",
      "peak_native_pss",
      "peak_graphics_pss",
      "min_available",
      "safe",
      "error",
    )
  }
}

internal const val DEFAULT_CONTEXT_TOKENS = 8_192
internal const val MIN_CONTEXT_TOKENS = 4_096
internal val SUPPORTED_CONTEXT_TOKENS = listOf(4_096, 8_192, 16_384, 32_768)
