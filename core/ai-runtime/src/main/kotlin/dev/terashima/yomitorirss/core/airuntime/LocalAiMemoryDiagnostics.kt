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

internal enum class LocalAiEngineCloseStatus(val wireName: String) {
  SUCCESS("success"),
  FAILED("failed"),
}

object LocalAiMemoryDiagnostics {
  private const val PREFERENCES_NAME = "local_ai_memory_diagnostics"
  private const val REPORT_KEY = "recent_vision_memory_samples"
  private const val MAX_SAMPLES = 64
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
    runCatching {
      val runtime = Runtime.getRuntime()
      val line = buildVisionMemoryDiagnosticLine(
        timestamp = System.currentTimeMillis(),
        pid = Process.myPid(),
        processName = Application.getProcessName(),
        phase = phase,
        pssKb = Debug.getPss().toLong(),
        rssKb = processRssKb(),
        nativeHeapKb = Debug.getNativeHeapAllocatedSize() / BYTES_PER_KIB,
        javaHeapKb = (runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_KIB,
        engineCloseStatus = engineCloseStatus,
        engineCloseErrorClass = engineCloseErrorClass,
      )
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

  fun recentVisionInferenceReport(
    context: Context,
    pid: Int,
    processName: String,
    untilTimestamp: Long,
  ): String? {
    val report = context.applicationContext
      .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
      .getString(REPORT_KEY, null)
    return filterDiagnosticLines(
      report = report,
      pid = pid,
      processName = processName,
      untilTimestamp = untilTimestamp,
    )
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
  append("timestamp=")
  append(timestamp)
  append(" pid=")
  append(pid)
  append(" process=")
  append(sanitizeProcessName(processName))
  append(" phase=")
  append(phase.wireName)
  append(" pssKb=")
  append(pssKb)
  append(" rssKb=")
  append(rssKb?.toString() ?: "unknown")
  append(" nativeHeapKb=")
  append(nativeHeapKb)
  append(" javaHeapKb=")
  append(javaHeapKb)
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
    .joinToString("\n")
    .takeIf(String::isNotBlank)
}

private fun diagnosticField(line: String, name: String): String? =
  line.split(' ')
    .firstOrNull { it.startsWith("$name=") }
    ?.substringAfter('=')

private fun sanitizeProcessName(processName: String): String =
  processName
    .filter { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' || it == ':' }
    .take(200)
    .ifBlank { "unknown" }
