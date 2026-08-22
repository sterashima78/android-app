package dev.terashima.yomitorirss.core.airuntime

import android.content.Context
import android.os.Debug
import android.util.Log
import java.io.File

enum class LocalAiMemoryDiagnosticPhase(val wireName: String) {
  VISION_BEFORE("vision-before"),
  VISION_AFTER_ENGINE_RELEASE("vision-after-engine-release"),
}

object LocalAiMemoryDiagnostics {
  private const val PREFERENCES_NAME = "local_ai_memory_diagnostics"
  private const val REPORT_KEY = "recent_vision_memory_samples"
  private const val MAX_SAMPLES = 24
  private const val TAG = "LocalAiMemory"
  private const val BYTES_PER_KIB = 1024L

  fun recordVisionInference(context: Context, phase: LocalAiMemoryDiagnosticPhase) {
    runCatching {
      val runtime = Runtime.getRuntime()
      val line = buildString {
        append("timestamp=")
        append(System.currentTimeMillis())
        append(" phase=")
        append(phase.wireName)
        append(" pssKb=")
        append(Debug.getPss())
        append(" rssKb=")
        append(processRssKb()?.toString() ?: "unknown")
        append(" nativeHeapKb=")
        append(Debug.getNativeHeapAllocatedSize() / BYTES_PER_KIB)
        append(" javaHeapKb=")
        append((runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_KIB)
      }
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

  fun recentVisionInferenceReport(context: Context): String? =
    context.applicationContext
      .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
      .getString(REPORT_KEY, null)
      ?.takeIf(String::isNotBlank)

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
