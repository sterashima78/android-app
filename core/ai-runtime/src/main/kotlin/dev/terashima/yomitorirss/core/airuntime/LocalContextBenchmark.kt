package dev.terashima.yomitorirss.core.airuntime

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import android.os.SystemClock
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock
import kotlin.math.max

class LocalContextBenchmarkRunner(
  context: Context,
  private val modelManager: LocalModelManager,
) {
  private val appContext = context.applicationContext
  private val activityManager = appContext.getSystemService(ActivityManager::class.java)
  private val store = LocalContextBenchmarkStore(appContext)

  fun runSelectedModelContexts(): LocalContextBenchmarkReport {
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

    val settings = modelManager.inferenceSettings.value
    val speculativeDecodingEnabled =
      settings.speculativeDecodingEnabled && model.supportsSpeculativeDecoding
    val totalDeviceMemoryBytes = systemMemory().totalMem
    val samples = mutableListOf<LocalContextBenchmarkSample>()

    modelManager.releaseRetainedInferenceForBenchmark()

    val contextCandidates = SUPPORTED_CONTEXT_TOKENS.filter {
      it <= modelManager.maxSupportedContextTokens(model.id)
    }
    for (contextTokens in contextCandidates) {
      val prompt = buildPrefillPrompt(contextTokens)
      val sample = runSample(
        modelFile = modelFile,
        modelId = model.id,
        backend = settings.backend,
        speculativeDecodingEnabled = speculativeDecodingEnabled,
        contextTokens = contextTokens,
        prompt = prompt,
        totalDeviceMemoryBytes = totalDeviceMemoryBytes,
      )
      samples += sample

      store.save(
        report(
          modelId = model.id,
          modelName = model.name,
          backend = settings.backend,
          speculativeDecodingEnabled = speculativeDecodingEnabled,
          totalDeviceMemoryBytes = totalDeviceMemoryBytes,
          samples = samples,
        ),
      )
      modelManager.refreshModels()

      if (!sample.succeeded || !sample.safe) break
      val minimumHeadroom = requiredAvailableMemory(totalDeviceMemoryBytes)
      if (sample.minimumAvailableMemoryBytes < minimumHeadroom * NEXT_CONTEXT_HEADROOM_MULTIPLIER) break
    }

    return report(
      modelId = model.id,
      modelName = model.name,
      backend = settings.backend,
      speculativeDecodingEnabled = speculativeDecodingEnabled,
      totalDeviceMemoryBytes = totalDeviceMemoryBytes,
      samples = samples,
    ).also {
      store.save(it)
      modelManager.refreshModels()
    }
  }

  fun lastSelectedModelReport(): LocalContextBenchmarkReport? {
    val model = modelManager.selectedModel() ?: return null
    val settings = modelManager.inferenceSettings.value
    return store.load(
      modelId = model.id,
      modelName = model.name,
      backend = settings.backend,
      speculativeDecodingEnabled =
        settings.speculativeDecodingEnabled && model.supportsSpeculativeDecoding,
    )
  }

  private fun report(
    modelId: String,
    modelName: String,
    backend: LocalInferenceBackend,
    speculativeDecodingEnabled: Boolean,
    totalDeviceMemoryBytes: Long,
    samples: List<LocalContextBenchmarkSample>,
  ) = LocalContextBenchmarkReport(
    modelId = modelId,
    modelName = modelName,
    backend = backend,
    speculativeDecodingEnabled = speculativeDecodingEnabled,
    totalDeviceMemoryBytes = totalDeviceMemoryBytes,
    recommendedContextTokens = chooseRecommendedContextTokens(samples),
    measuredAtEpochMillis = System.currentTimeMillis(),
    samples = samples.toList(),
  )

  @OptIn(ExperimentalApi::class)
  private fun runSample(
    modelFile: File,
    modelId: String,
    backend: LocalInferenceBackend,
    speculativeDecodingEnabled: Boolean,
    contextTokens: Int,
    prompt: String,
    totalDeviceMemoryBytes: Long,
  ): LocalContextBenchmarkSample = contextBenchmarkLock.withLock {
    val requestedPrefillTokens = modelManager.countTokens(prompt)
    val sampler = ProcessMemorySampler(activityManager)
    val baseline = sampler.start()
    var engine: Engine? = null
    try {
      val initStartedAt = SystemClock.elapsedRealtime()
      val previousSpeculativeDecoding = ExperimentalFlags.enableSpeculativeDecoding
      ExperimentalFlags.enableSpeculativeDecoding = speculativeDecodingEnabled
      engine = try {
        Engine(
          EngineConfig(
            modelPath = modelFile.absolutePath,
            backend = when (backend) {
              LocalInferenceBackend.CPU -> Backend.CPU()
              LocalInferenceBackend.GPU -> Backend.GPU()
            },
            cacheDir = benchmarkCacheDirectory(
              modelId = modelId,
              backend = backend,
              speculativeDecodingEnabled = speculativeDecodingEnabled,
              contextTokens = contextTokens,
            ).absolutePath,
            maxNumTokens = contextTokens,
          ),
        ).also { it.initialize() }
      } finally {
        ExperimentalFlags.enableSpeculativeDecoding = previousSpeculativeDecoding
      }
      val initTimeMillis = SystemClock.elapsedRealtime() - initStartedAt

      val inferenceStartedAt = SystemClock.elapsedRealtime()
      engine.createConversation().use { conversation ->
        conversation.sendMessage(prompt)
      }
      val inferenceTimeMillis = SystemClock.elapsedRealtime() - inferenceStartedAt

      engine.close()
      engine = null
      val memory = sampler.stop()
      LocalContextBenchmarkSample(
        contextTokens = contextTokens,
        requestedPrefillTokens = requestedPrefillTokens,
        initTimeMillis = initTimeMillis,
        inferenceTimeMillis = inferenceTimeMillis,
        baselinePssBytes = baseline.totalPssBytes,
        peakPssBytes = memory.peakTotalPssBytes,
        peakNativePssBytes = memory.peakNativePssBytes,
        peakGraphicsPssBytes = memory.peakGraphicsPssBytes,
        minimumAvailableMemoryBytes = memory.minimumAvailableMemoryBytes,
        safe = isMemorySafe(
          totalDeviceMemoryBytes = totalDeviceMemoryBytes,
          peakPssBytes = memory.peakTotalPssBytes,
          minimumAvailableMemoryBytes = memory.minimumAvailableMemoryBytes,
          observedLowMemory = memory.observedLowMemory,
        ),
      )
    } catch (error: Throwable) {
      runCatching { engine?.close() }
      val memory = sampler.stop()
      LocalContextBenchmarkSample(
        contextTokens = contextTokens,
        requestedPrefillTokens = requestedPrefillTokens,
        initTimeMillis = 0,
        inferenceTimeMillis = 0,
        baselinePssBytes = baseline.totalPssBytes,
        peakPssBytes = memory.peakTotalPssBytes,
        peakNativePssBytes = memory.peakNativePssBytes,
        peakGraphicsPssBytes = memory.peakGraphicsPssBytes,
        minimumAvailableMemoryBytes = memory.minimumAvailableMemoryBytes,
        safe = false,
        error = error.userMessage(),
      )
    }
  }

  private fun buildPrefillPrompt(contextTokens: Int): String {
    val targetTokens = (contextTokens * PREFILL_CONTEXT_RATIO_PERCENT / 100 - OUTPUT_RESERVE_TOKENS)
      .coerceAtLeast(MIN_PREFILL_TOKENS)
    val header = "次の本文を読み、最後に『確認しました』とだけ回答してください。\n\n本文:\n"
    val unit = "これはコンテキスト容量とメモリ使用量を測定するための技術記事本文です。固有名詞、数値、因果関係を含む文章として処理してください。\n"

    var lower = 0
    var upper = max(1, targetTokens / 4)
    while (modelManager.countTokens(header + unit.repeat(upper)) < targetTokens) {
      lower = upper
      upper *= 2
    }
    while (lower + 1 < upper) {
      val middle = lower + (upper - lower) / 2
      val tokens = modelManager.countTokens(header + unit.repeat(middle))
      if (tokens <= targetTokens) lower = middle else upper = middle
    }
    return header + unit.repeat(lower.coerceAtLeast(1))
  }

  private fun benchmarkCacheDirectory(
    modelId: String,
    backend: LocalInferenceBackend,
    speculativeDecodingEnabled: Boolean,
    contextTokens: Int,
  ): File = File(
    appContext.cacheDir,
    "$MODEL_DIRECTORY/$modelId/${backend.name.lowercase()}/context-benchmark/" +
      "${if (speculativeDecodingEnabled) "speculative" else "standard"}/$contextTokens",
  ).apply { mkdirs() }

  private fun systemMemory(): ActivityManager.MemoryInfo = ActivityManager.MemoryInfo().also {
    activityManager.getMemoryInfo(it)
  }

  private fun Throwable.userMessage(): String =
    generateSequence(this) { it.cause }
      .mapNotNull(Throwable::message)
      .firstOrNull(String::isNotBlank)
      ?: javaClass.simpleName

  private fun benchmarkModelFileName(modelId: String): String = when (modelId) {
    "gemma4-e2b-it" -> "gemma-4-E2B-it.litertlm"
    "gemma4-e4b-it" -> "gemma-4-E4B-it.litertlm"
    else -> error("このモデルはコンテキストベンチマークに対応していません: $modelId")
  }

  companion object {
    private const val MODEL_DIRECTORY = "local-summary-models"
    private const val PREFILL_CONTEXT_RATIO_PERCENT = 75
    private const val OUTPUT_RESERVE_TOKENS = 512
    private const val MIN_PREFILL_TOKENS = 1_024
    private const val ABSOLUTE_MEMORY_HEADROOM_BYTES = 1L * 1024 * 1024 * 1024
    private const val MEMORY_HEADROOM_PERCENT = 15
    private const val MAX_PROCESS_PSS_PERCENT = 70
    private const val NEXT_CONTEXT_HEADROOM_MULTIPLIER = 2
    private val contextBenchmarkLock = ReentrantLock()

    internal fun requiredAvailableMemory(totalDeviceMemoryBytes: Long): Long =
      max(
        ABSOLUTE_MEMORY_HEADROOM_BYTES,
        totalDeviceMemoryBytes * MEMORY_HEADROOM_PERCENT / 100,
      )

    internal fun isMemorySafe(
      totalDeviceMemoryBytes: Long,
      peakPssBytes: Long,
      minimumAvailableMemoryBytes: Long,
      observedLowMemory: Boolean,
    ): Boolean =
      !observedLowMemory &&
        minimumAvailableMemoryBytes >= requiredAvailableMemory(totalDeviceMemoryBytes) &&
        peakPssBytes <= totalDeviceMemoryBytes * MAX_PROCESS_PSS_PERCENT / 100
  }
}

private data class ProcessMemorySnapshot(
  val totalPssBytes: Long,
  val nativePssBytes: Long,
  val graphicsPssBytes: Long,
  val availableMemoryBytes: Long,
  val lowMemory: Boolean,
)

private data class ProcessMemorySampleSummary(
  val peakTotalPssBytes: Long,
  val peakNativePssBytes: Long,
  val peakGraphicsPssBytes: Long,
  val minimumAvailableMemoryBytes: Long,
  val observedLowMemory: Boolean,
)

private class ProcessMemorySampler(
  private val activityManager: ActivityManager,
) {
  private val running = AtomicBoolean(false)
  private val lock = Any()
  private var thread: Thread? = null
  private var peakTotalPssBytes = 0L
  private var peakNativePssBytes = 0L
  private var peakGraphicsPssBytes = 0L
  private var minimumAvailableMemoryBytes = Long.MAX_VALUE
  private var observedLowMemory = false

  fun start(): ProcessMemorySnapshot {
    val initial = snapshot()
    record(initial)
    running.set(true)
    thread = thread(name = "local-ai-memory-sampler", isDaemon = true) {
      while (running.get()) {
        record(snapshot())
        try {
          Thread.sleep(SAMPLE_INTERVAL_MILLIS)
        } catch (_: InterruptedException) {
          break
        }
      }
    }
    return initial
  }

  fun stop(): ProcessMemorySampleSummary {
    running.set(false)
    thread?.interrupt()
    runCatching { thread?.join(STOP_JOIN_TIMEOUT_MILLIS) }
    record(snapshot())
    return synchronized(lock) {
      ProcessMemorySampleSummary(
        peakTotalPssBytes = peakTotalPssBytes,
        peakNativePssBytes = peakNativePssBytes,
        peakGraphicsPssBytes = peakGraphicsPssBytes,
        minimumAvailableMemoryBytes = minimumAvailableMemoryBytes.takeIf { it != Long.MAX_VALUE } ?: 0,
        observedLowMemory = observedLowMemory,
      )
    }
  }

  private fun record(snapshot: ProcessMemorySnapshot) = synchronized(lock) {
    peakTotalPssBytes = max(peakTotalPssBytes, snapshot.totalPssBytes)
    peakNativePssBytes = max(peakNativePssBytes, snapshot.nativePssBytes)
    peakGraphicsPssBytes = max(peakGraphicsPssBytes, snapshot.graphicsPssBytes)
    minimumAvailableMemoryBytes = minOf(minimumAvailableMemoryBytes, snapshot.availableMemoryBytes)
    observedLowMemory = observedLowMemory || snapshot.lowMemory
  }

  private fun snapshot(): ProcessMemorySnapshot {
    val processInfo = activityManager.getProcessMemoryInfo(intArrayOf(Process.myPid())).first()
    val systemInfo = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
    val graphicsPssKb = processInfo.memoryStats["summary.graphics"]?.toLongOrNull() ?: 0L
    return ProcessMemorySnapshot(
      totalPssBytes = processInfo.totalPss.toLong() * BYTES_PER_KB,
      nativePssBytes = processInfo.nativePss.toLong() * BYTES_PER_KB,
      graphicsPssBytes = graphicsPssKb * BYTES_PER_KB,
      availableMemoryBytes = systemInfo.availMem,
      lowMemory = systemInfo.lowMemory,
    )
  }

  companion object {
    private const val BYTES_PER_KB = 1024L
    private const val SAMPLE_INTERVAL_MILLIS = 125L
    private const val STOP_JOIN_TIMEOUT_MILLIS = 1_000L
  }
}
