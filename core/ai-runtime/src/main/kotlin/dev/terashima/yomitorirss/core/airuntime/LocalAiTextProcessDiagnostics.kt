package dev.terashima.yomitorirss.core.airuntime

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.Process
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal enum class LocalAiTextProcessMode(val wireName: String) {
  TEXT("text"),
  STRUCTURED("structured"),
}

internal enum class LocalAiTextProcessPhase(val wireName: String) {
  BOUND("bound"),
  REQUEST_RECEIVED("request"),
  PREPARING_MODEL("prepare"),
  GENERATING_RESPONSE("generate"),
  COMPLETED("complete"),
  RECYCLE("recycle"),
}

/**
 * Diagnostics for the short-lived :local_ai_text subprocess.
 *
 * Only process/runtime metadata is recorded. Prompt, model output, book/article metadata, URLs and
 * file paths are intentionally excluded so the resulting report remains safe to share.
 */
object LocalAiTextProcessDiagnostics {
  private const val DIAGNOSTICS_DIRECTORY = "diagnostics"
  private const val FILE_PREFIX = "local-ai-text-"
  private const val FILE_SUFFIX = ".log"
  private const val MAX_SAMPLES = 72
  private const val MAX_RETAINED_FILES = 6
  private const val SAMPLE_INTERVAL_MILLIS = 10_000L
  private const val REPORT_WINDOW_MILLIS = 10 * 60_000L
  internal const val PROCESS_STATE_SUMMARY_MAX_BYTES = 128
  private const val BYTES_PER_KIB = 1024L

  private val processInitialized = AtomicBoolean(false)
  private val diagnosticScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val fileLock = Any()
  @Volatile
  private var activeState: LocalAiTextProcessDiagnosticState? = null
  @Volatile
  private var appContext: Context? = null
  private var samplerJob: Job? = null

  internal fun startSession(
    context: Context,
    mode: LocalAiTextProcessMode,
  ): LocalAiTextProcessDiagnosticSession =
    LocalAiTextProcessDiagnosticSession(
      context = context.applicationContext,
      mode = mode,
    )

  @Suppress("UNUSED_PARAMETER")
  internal fun startSession(
    context: Context,
    scope: CoroutineScope,
    mode: LocalAiTextProcessMode,
  ): LocalAiTextProcessDiagnosticSession = startSession(context, mode)

  fun recentProcessReport(
    context: Context,
    pid: Int,
    untilTimestamp: Long,
  ): String? {
    val file = diagnosticFile(context.applicationContext, pid)
    if (!file.isFile) return null
    return filterLocalAiTextProcessDiagnosticLines(
      report = runCatching { file.readText() }.getOrNull(),
      pid = pid,
      untilTimestamp = untilTimestamp,
      windowMillis = REPORT_WINDOW_MILLIS,
    )
  }

  internal fun activate(
    context: Context,
    state: LocalAiTextProcessDiagnosticState,
  ) {
    val applicationContext = context.applicationContext
    appContext = applicationContext
    val pid = Process.myPid()
    if (processInitialized.compareAndSet(false, true)) {
      initializeFile(applicationContext, pid)
      samplerJob = diagnosticScope.launch {
        while (isActive) {
          delay(SAMPLE_INTERVAL_MILLIS)
          recordActiveSample()
        }
      }
    }
    updateState(applicationContext, state)
  }

  internal fun updateState(
    context: Context,
    state: LocalAiTextProcessDiagnosticState,
  ) {
    activeState = state
    publishProcessStateSummary(context, state)
    recordSample(context, state)
  }

  private fun recordActiveSample() {
    val context = appContext ?: return
    val state = activeState ?: return
    recordSample(context, state)
  }

  private fun recordSample(
    context: Context,
    state: LocalAiTextProcessDiagnosticState,
  ) {
    val runtime = Runtime.getRuntime()
    val line = buildLocalAiTextProcessDiagnosticLine(
      timestamp = System.currentTimeMillis(),
      pid = Process.myPid(),
      processName = Application.getProcessName(),
      state = state,
      pssKb = Debug.getPss().toLong(),
      rssKb = processRssKb(),
      nativeHeapKb = Debug.getNativeHeapAllocatedSize() / BYTES_PER_KIB,
      javaHeapKb = (runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_KIB,
    )
    appendSample(context, line, Process.myPid())
  }

  private fun initializeFile(context: Context, pid: Int) {
    synchronized(fileLock) {
      runCatching {
        val file = diagnosticFile(context, pid)
        file.parentFile?.mkdirs()
        file.writeText("")
        file.parentFile
          ?.listFiles { candidate ->
            candidate.isFile && candidate.name.startsWith(FILE_PREFIX) && candidate.name.endsWith(FILE_SUFFIX)
          }
          ?.sortedByDescending(File::lastModified)
          ?.drop(MAX_RETAINED_FILES)
          ?.forEach { stale -> runCatching { stale.delete() } }
      }
    }
  }

  private fun appendSample(context: Context, line: String, pid: Int) {
    synchronized(fileLock) {
      runCatching {
        val file = diagnosticFile(context, pid)
        file.parentFile?.mkdirs()
        val previous = if (file.isFile) file.readLines() else emptyList()
        file.writeText((previous.filter(String::isNotBlank) + line).takeLast(MAX_SAMPLES).joinToString("\n"))
      }
    }
  }

  private fun diagnosticFile(context: Context, pid: Int): File =
    File(File(context.filesDir, DIAGNOSTICS_DIRECTORY), "$FILE_PREFIX$pid$FILE_SUFFIX")
}

internal class LocalAiTextProcessDiagnosticSession(
  private val context: Context,
  private val mode: LocalAiTextProcessMode,
) {
  @Volatile
  private var state = LocalAiTextProcessDiagnosticState(
    mode = mode,
    phase = LocalAiTextProcessPhase.BOUND,
    backend = null,
    contextTokens = null,
    speculativeDecodingEnabled = null,
  )

  fun start() {
    LocalAiTextProcessDiagnostics.activate(context, state)
  }

  fun mark(
    phase: LocalAiTextProcessPhase,
    backend: LocalInferenceBackend? = state.backend,
    contextTokens: Int? = state.contextTokens,
    speculativeDecodingEnabled: Boolean? = state.speculativeDecodingEnabled,
  ) {
    state = LocalAiTextProcessDiagnosticState(
      mode = mode,
      phase = phase,
      backend = backend,
      contextTokens = contextTokens,
      speculativeDecodingEnabled = speculativeDecodingEnabled,
    )
    LocalAiTextProcessDiagnostics.updateState(context, state)
  }

  fun stop() {
    mark(LocalAiTextProcessPhase.RECYCLE)
  }
}

internal data class LocalAiTextProcessDiagnosticState(
  val mode: LocalAiTextProcessMode,
  val phase: LocalAiTextProcessPhase,
  val backend: LocalInferenceBackend?,
  val contextTokens: Int?,
  val speculativeDecodingEnabled: Boolean?,
)

internal fun buildLocalAiTextProcessStateSummary(state: LocalAiTextProcessDiagnosticState): String =
  buildString {
    append("ai=")
    append(state.mode.wireName)
    append(";phase=")
    append(state.phase.wireName)
    state.backend?.let {
      append(";backend=")
      append(it.name.lowercase(Locale.ROOT))
    }
    state.contextTokens?.let {
      append(";ctx=")
      append(it)
    }
    state.speculativeDecodingEnabled?.let {
      append(";spec=")
      append(if (it) '1' else '0')
    }
  }

private fun publishProcessStateSummary(
  context: Context,
  state: LocalAiTextProcessDiagnosticState,
) {
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
  runCatching {
    val bytes = buildLocalAiTextProcessStateSummary(state).toByteArray(StandardCharsets.US_ASCII)
    val summary = if (bytes.size <= LocalAiTextProcessDiagnostics.PROCESS_STATE_SUMMARY_MAX_BYTES) {
      bytes
    } else {
      bytes.copyOf(LocalAiTextProcessDiagnostics.PROCESS_STATE_SUMMARY_MAX_BYTES)
    }
    context.getSystemService(ActivityManager::class.java).setProcessStateSummary(summary)
  }
}

internal fun buildLocalAiTextProcessDiagnosticLine(
  timestamp: Long,
  pid: Int,
  processName: String,
  state: LocalAiTextProcessDiagnosticState,
  pssKb: Long,
  rssKb: Long?,
  nativeHeapKb: Long,
  javaHeapKb: Long,
): String = buildString {
  append("timestamp=")
  append(timestamp)
  append(" pid=")
  append(pid)
  append(" process=")
  append(sanitizeLocalAiDiagnosticValue(processName, 200))
  append(" mode=")
  append(state.mode.wireName)
  append(" phase=")
  append(state.phase.wireName)
  append(" pssKb=")
  append(pssKb)
  append(" rssKb=")
  append(rssKb?.toString() ?: "unknown")
  append(" nativeHeapKb=")
  append(nativeHeapKb)
  append(" javaHeapKb=")
  append(javaHeapKb)
  state.backend?.let {
    append(" backend=")
    append(it.name.lowercase(Locale.ROOT))
  }
  state.contextTokens?.let {
    append(" contextTokens=")
    append(it)
  }
  state.speculativeDecodingEnabled?.let {
    append(" speculativeDecoding=")
    append(it)
  }
}

internal fun filterLocalAiTextProcessDiagnosticLines(
  report: String?,
  pid: Int,
  untilTimestamp: Long,
  windowMillis: Long,
): String? {
  val earliestTimestamp = (untilTimestamp - windowMillis).coerceAtLeast(0L)
  return report
    .orEmpty()
    .lineSequence()
    .filter(String::isNotBlank)
    .filter { diagnosticField(it, "pid")?.toIntOrNull() == pid }
    .filter { line ->
      diagnosticField(line, "timestamp")
        ?.toLongOrNull()
        ?.let { it in earliestTimestamp..untilTimestamp } == true
    }
    .sortedBy { diagnosticField(it, "timestamp")?.toLongOrNull() ?: Long.MIN_VALUE }
    .joinToString("\n")
    .takeIf(String::isNotBlank)
}

private fun processRssKb(): Long? = runCatching {
  File("/proc/self/status").useLines { lines ->
    val rssLine = lines.firstOrNull { it.startsWith("VmRSS:") } ?: return@useLines null
    rssLine
      .trim()
      .split(Regex("\\s+"))
      .getOrNull(1)
      ?.toLongOrNull()
  }
}.getOrNull()

private fun diagnosticField(line: String, name: String): String? =
  line.split(' ')
    .firstOrNull { it.startsWith("$name=") }
    ?.substringAfter('=')

private fun sanitizeLocalAiDiagnosticValue(value: String, maxLength: Int): String =
  value
    .filter { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' || it == ':' }
    .take(maxLength)
    .ifBlank { "unknown" }
