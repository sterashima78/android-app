package dev.terashima.yomitorirss.core.airuntime

import android.content.Context
import android.os.SystemClock
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.BenchmarkInfo
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.benchmark
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.roundToLong

data class LocalModelBenchmarkSample(
  val speculativeDecodingEnabled: Boolean,
  val initTimeMillis: Long,
  val timeToFirstTokenMillis: Long,
  val prefillTokenCount: Int,
  val decodeTokenCount: Int,
  val prefillTokensPerSecond: Double,
  val decodeTokensPerSecond: Double,
  val totalTimeMillis: Long,
)

data class LocalModelBenchmarkComparison(
  val modelId: String,
  val modelName: String,
  val backend: LocalInferenceBackend,
  val requestedPrefillTokens: Int,
  val requestedDecodeTokens: Int,
  val standard: LocalModelBenchmarkSample,
  val speculative: LocalModelBenchmarkSample?,
  val speculativeError: String? = null,
)

class LocalModelBenchmarkRunner(
  context: Context,
  private val modelManager: LocalModelManager,
) {
  private val appContext = context.applicationContext

  fun runSelectedModelComparison(): LocalModelBenchmarkComparison {
    check(modelManager.isSupported()) { "この端末ではローカルモデルを利用できません" }
    val model = modelManager.selectedModel() ?: error("AIモデルをダウンロードして選択してください")
    check(model.downloaded) { "選択したモデルがダウンロードされていません" }

    val modelFile = File(
      File(appContext.filesDir, MODEL_DIRECTORY),
      benchmarkModelFileName(model.id),
    )
    check(modelFile.isFile && modelFile.length() == model.sizeBytes) {
      "選択したモデルファイルが見つかりません"
    }

    // Release only the retained interactive Engine. LocalModelManager itself remains reusable and
    // keeps its inference-session tracker alive for subsequent Summary/Chat requests.
    modelManager.releaseRetainedInferenceForBenchmark()

    val backend = modelManager.inferenceSettings.value.backend
    val standard = runSample(
      modelFile = modelFile,
      modelId = model.id,
      backend = backend,
      speculativeDecodingEnabled = false,
    )
    val speculativeResult = if (model.supportsSpeculativeDecoding) {
      runCatching {
        runSample(
          modelFile = modelFile,
          modelId = model.id,
          backend = backend,
          speculativeDecodingEnabled = true,
        )
      }
    } else {
      null
    }

    return LocalModelBenchmarkComparison(
      modelId = model.id,
      modelName = model.name,
      backend = backend,
      requestedPrefillTokens = BENCHMARK_PREFILL_TOKENS,
      requestedDecodeTokens = BENCHMARK_DECODE_TOKENS,
      standard = standard,
      speculative = speculativeResult?.getOrNull(),
      speculativeError = speculativeResult?.exceptionOrNull()?.userMessage(),
    )
  }

  @OptIn(ExperimentalApi::class)
  private fun runSample(
    modelFile: File,
    modelId: String,
    backend: LocalInferenceBackend,
    speculativeDecodingEnabled: Boolean,
  ): LocalModelBenchmarkSample = benchmarkLock.withLock {
    val previousSpeculativeDecoding = ExperimentalFlags.enableSpeculativeDecoding
    ExperimentalFlags.enableSpeculativeDecoding = speculativeDecodingEnabled
    try {
      val startedAt = SystemClock.elapsedRealtime()
      val info = benchmark(
        modelPath = modelFile.absolutePath,
        backend = when (backend) {
          LocalInferenceBackend.CPU -> Backend.CPU()
          LocalInferenceBackend.GPU -> Backend.GPU()
        },
        prefillTokens = BENCHMARK_PREFILL_TOKENS,
        decodeTokens = BENCHMARK_DECODE_TOKENS,
        cacheDir = benchmarkCacheDirectory(modelId, backend, speculativeDecodingEnabled).absolutePath,
        prompt = BENCHMARK_PROMPT,
      )
      val totalTimeMillis = SystemClock.elapsedRealtime() - startedAt
      info.toSample(speculativeDecodingEnabled, totalTimeMillis)
    } finally {
      ExperimentalFlags.enableSpeculativeDecoding = previousSpeculativeDecoding
    }
  }

  private fun benchmarkCacheDirectory(
    modelId: String,
    backend: LocalInferenceBackend,
    speculativeDecodingEnabled: Boolean,
  ): File = File(
    appContext.cacheDir,
    "$MODEL_DIRECTORY/$modelId/${backend.name.lowercase()}/benchmark/${if (speculativeDecodingEnabled) "speculative" else "standard"}",
  ).apply { mkdirs() }

  private fun BenchmarkInfo.toSample(
    speculativeDecodingEnabled: Boolean,
    totalTimeMillis: Long,
  ) = LocalModelBenchmarkSample(
    speculativeDecodingEnabled = speculativeDecodingEnabled,
    initTimeMillis = (initTimeInSecond * MILLIS_PER_SECOND).roundToLong(),
    timeToFirstTokenMillis = (timeToFirstTokenInSecond * MILLIS_PER_SECOND).roundToLong(),
    prefillTokenCount = lastPrefillTokenCount,
    decodeTokenCount = lastDecodeTokenCount,
    prefillTokensPerSecond = lastPrefillTokensPerSecond,
    decodeTokensPerSecond = lastDecodeTokensPerSecond,
    totalTimeMillis = totalTimeMillis,
  )

  private fun benchmarkModelFileName(modelId: String): String = when (modelId) {
    "gemma4-e2b-it" -> "gemma-4-E2B-it.litertlm"
    "gemma4-e4b-it" -> "gemma-4-E4B-it.litertlm"
    else -> error("このモデルはベンチマークに対応していません: $modelId")
  }

  private fun Throwable.userMessage(): String =
    generateSequence(this) { it.cause }
      .mapNotNull(Throwable::message)
      .firstOrNull(String::isNotBlank)
      ?: javaClass.simpleName

  companion object {
    private const val MODEL_DIRECTORY = "local-summary-models"
    private const val BENCHMARK_PREFILL_TOKENS = 2048
    private const val BENCHMARK_DECODE_TOKENS = 128
    private const val MILLIS_PER_SECOND = 1000.0
    private const val BENCHMARK_PROMPT =
      "次の記事本文を日本語で要約してください。重要な主張、根拠、数値、固有名詞を落とさず、簡潔にまとめてください。"

    private val benchmarkLock = ReentrantLock()
  }
}
