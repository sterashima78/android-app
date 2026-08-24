package dev.terashima.yomitorirss.core.airuntime

import android.app.Application
import android.content.Context
import android.os.Debug
import android.os.Process
import android.util.Log
import java.io.File

enum class LocalAiMemoryDiagnosticPhase(val wireName: String) {
  VISION_BEFORE("vision-before"),
  VISION_AFTER_ENGINE_INIT("vision-after-engine-init"),
  VISION_AFTER_INFERENCE("vision-after-inference"),
  VISION_AFTER_ENGINE_RELEASE("vision-after-engine-release"),
}

enum class LocalAiProcessMemoryPhase(val wireName: String) {
  ACTIVE_BACKGROUND_AI("main-active-background-ai"),
  RETAINED_AFTER_BACKGROUND_AI("main-retained-after-background-ai"),
}

internal enum class LocalAiEngineCloseStatus(val wireName: String) {
  SUCCESS("success"),
  FAILED("failed"),
}

object LocalAiMemoryDiagnostics {
  private const val PREFERENCES_NAME = "local_ai_memory_diagnostics"
  private const val REPORT_KEY = "recent_inference_memory_samples"
  private const val LEGACY_REPORT_KEY = "recent_vision_memory_samples"
  private const val MAX_SAMPLES = 128
  private const val TAG = "LocalAiMemory"
  private const val BYTES_PER_KIB = 1024L

  fun recordVisionInference(
    context: Context,
    phase: LocalAiMemoryDiagnosticPhase,
  ) {
    recordVisionInference(
      context = context,
      phase = phase,
      engineCloseStatus = null,
      engineCloseErrorClass = null,
    )
  }

  internal fun recordVisionInference(
    context: Context,
    phase: LocalAiMemoryDiagnosticPhase,
    engineCloseStatus: LocalAiEngineCloseStatus?,
    engineCloseErrorClass: String?,
  ) {
    recordLine(context) { snapshot ->
      buildVisionMemoryDiagnosticLine(
        timestamp = snapshot.timestamp,
        pid = snapshot.pid,
        processName = snapshot.processName,
        phase = phase,
        pssKb = snapshot.pssKb,
        rssKb = snapshot.rssKb,
        nativeHeapKb = snapshot.nativeHeapKb,
        javaHeapKb = snapshot.javaHeapKb,
        engineCloseStatus = engineCloseStatus,
        engineCloseErrorClass = engineCloseErrorClass,
      )
    }
  }

  fun recordProcessSample(
    context: Context,
    phase: LocalAiProcessMemoryPhase,
    diagnosticLabel: String,
  ) {
    recordLine(context) { snapshot ->
      buildProcessMemoryDiagnosticLine(
        timestamp = snapshot.timestamp,
        pid = snapshot.pid,
        processName = snapshot.processName,
        phase = phase,
        diagnosticLabel = diagnosticLabel,
        pssKb = snapshot.pssKb,
        rssKb = snapshot.rssKb,
        nativeHeapKb = snapshot.nativeHeapKb,
        javaHeapKb = snapshot.javaHeapKb,
      )
    }
  }

  fun recentInferenceReport(
    context: Context,
    pid: Int,
    processName: String,
    untilTimestamp: Long,
  ): String? {
    val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    val report = sequenceOf(
      preferences.getString(LEGACY_REPORT_KEY, null),
      preferences.getString(REPORT_KEY, null),
    )
      .filterNotNull()
      .flatMap { it.lineSequence() }
      .filter(String::isNotBlank)
      .joinToString("\n")
    return filterDiagnosticLines(
      report = report,
      pid = pid,
      processName = processName,
      untilTimestamp = untilTimestamp,
    )
  }

  private fun recordLine(
    context: Context,
    buildLine: (MemorySnapshot) -> String,
  ) {
    runCatching {
      val runtime = Runtime.getRuntime()
      val snapshot = MemorySnapshot(
        timestamp = System.currentTimeMillis(),
        pid = Process.myPid(),
        processName = Application.getProcessName(),
        pssKb = Debug.getPss().toLong(),
        rssKb = processRssKb(),
        nativeHeapKb = Debug.getNativeHeapAllocatedSize() / BYTES_PER_KIB,
        javaHeapKb = (runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_KIB,
      )
      val line = buildLine(snapshot)
      Log.i(TAG, line)
      val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
      val updated = appendDiagnosticLine(
        existing = preferences.getString(REPORT_KEY, null),
        newLine = line,
        maxLines = MAX_SAMPLES,
      )
      preferences.edit().putString(REPORT_KEY, updated).commit()
    }
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

  private data class MemorySnapshot(
    val timestamp: Long,
    val pid: Int,
    val processName: String,
    val pssKb: Long,
    val rssKb: Long?,
    val nativeHeapKb: Long,
    val javaHeapKb: Long,
  )
}

internal fun buildVisionMemoryDiagnosticLine(
  timestamp: Long,
  pid: Int,
  processName: String,
  phase: LocalAiMemoryDiagnosticPhase,
  pssKb: Long,
  rssKb: Long?,
  nativeHeapKb: Long,
  javaHeapKb: Long,
  engineCloseStatus: LocalAiEngineCloseStatus? = null,
  engineCloseErrorClass: String? = null,
): String = buildString {
  appendMemoryFields(
    timestamp = timestamp,
    pid = pid,
    processName = processName,
    phase = phase.wireName,
    pssKb = pssKb,
    rssKb = rssKb,
    nativeHeapKb = nativeHeapKb,
    javaHeapKb = javaHeapKb,
  )
  engineCloseStatus?.let { status ->
    append(" engineClose=")
    append(status.wireName)
  }
  engineCloseErrorClass
    ?.takeIf(String::isNotBlank)
    ?.let { errorClass ->
      append(" engineCloseError=")
      append(errorClass.filter { it.isLetterOrDigit() || it == '.' || it == '$' || it == '_' }.take(160))
    }
}

internal fun buildProcessMemoryDiagnosticLine(
  timestamp: Long,
  pid: Int,
  processName: String,
  phase: LocalAiProcessMemoryPhase,
  diagnosticLabel: String,
  pssKb: Long,
  rssKb: Long?,
  nativeHeapKb: Long,
  javaHeapKb: Long,
): String = buildString {
  appendMemoryFields(
    timestamp = timestamp,
    pid = pid,
    processName = processName,
    phase = phase.wireName,
    pssKb = pssKb,
    rssKb = rssKb,
    nativeHeapKb = nativeHeapKb,
    javaHeapKb = javaHeapKb,
  )
  append(" task=")
  append(sanitizeDiagnosticLabel(diagnosticLabel))
}

private fun StringBuilder.appendMemoryFields(
  timestamp: Long,
  pid: Int,
  processName: String,
  phase: String,
  pssKb: Long,
  rssKb: Long?,
  nativeHeapKb: Long,
  javaHeapKb: Long,
) {
  append("timestamp=")
  append(timestamp)
  append(" pid=")
  append(pid)
  append(" process=")
  append(sanitizeProcessName(processName))
  append(" phase=")
  append(phase)
  append(" pssKb=")
  append(pssKb)
  append(" rssKb=")
  append(rssKb?.toString() ?: "unknown")
  append(" nativeHeapKb=")
  append(nativeHeapKb)
  append(" javaHeapKb=")
  append(javaHeapKb)
}

internal fun appendDiagnosticLine(
  existing: String?,
  newLine: String,
  maxLines: Int,
): String {
  require(maxLines > 0) { "maxLines must be positive" }
  val previous = existing
    .orEmpty()
    .lineSequence()
    .filter(String::isNotBlank)
    .toList()
  return (previous + newLine).takeLast(maxLines).joinToString("\n")
}

internal fun filterDiagnosticLines(
  report: String?,
  pid: Int,
  processName: String,
  untilTimestamp: Long,
): String? {
  val expectedProcessName = sanitizeProcessName(processName)
  return report
    .orEmpty()
    .lineSequence()
    .filter(String::isNotBlank)
    .filter { line -> diagnosticField(line, "pid")?.toIntOrNull() == pid }
    .filter { line -> diagnosticField(line, "process") == expectedProcessName }
    .filter { line ->
      diagnosticField(line, "timestamp")
        ?.toLongOrNull()
        ?.let { it <= untilTimestamp } == true
    }
    .sortedBy { line -> diagnosticField(line, "timestamp")?.toLongOrNull() ?: Long.MIN_VALUE }
    .joinToString("\n")
    .takeIf(String::isNotBlank)
}

private fun diagnosticField(line: String, name: String): String? =
  line.split(' ')
    .firstOrNull { it.startsWith("$name=") }
    ?.substringAfter('=')

private fun sanitizeDiagnosticLabel(value: String): String =
  value
    .filter { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' || it == ':' }
    .take(160)
    .ifBlank { "unknown" }

private fun sanitizeProcessName(processName: String): String =
  processName
    .filter { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' || it == ':' }
    .take(200)
    .ifBlank { "unknown" }
