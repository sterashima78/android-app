package dev.terashima.yomitorirss

import android.annotation.SuppressLint
import android.app.Application
import android.os.Build
import android.os.ProfilingManager
import android.os.ProfilingTrigger
import android.util.Log
import java.io.File
import java.util.concurrent.Executor
import java.util.function.Consumer

/**
 * Registers Android 17's anomaly profiling trigger without raising the project's compile/target SDK
 * baseline. compileSdk 36 contains ProfilingTrigger itself, while TRIGGER_TYPE_ANOMALY is added by
 * API 37, so the API-37 constant value is used only behind an SDK 37 runtime guard.
 */
internal object Android17MemoryAnomalyProfiler {
  private const val TAG = "MemoryAnomalyProfiler"
  private const val TRIGGER_TYPE_ANOMALY_API_37 = 8
  private val directExecutor = Executor(Runnable::run)
  private val listener = Consumer<android.os.ProfilingResult> { result ->
    val fileName = result.resultFilePath?.let(::File)?.name ?: "none"
    Log.i(
      TAG,
      "trigger=${result.triggerType} error=${result.errorCode} artifact=$fileName",
    )
  }

  @SuppressLint("NewApi", "WrongConstant")
  fun install(application: Application) {
    if (Build.VERSION.SDK_INT < 37) return
    runCatching {
      val manager = application.getSystemService(ProfilingManager::class.java) ?: return
      manager.registerForAllProfilingResults(directExecutor, listener)
      manager.addProfilingTriggers(
        listOf(
          ProfilingTrigger.Builder(TRIGGER_TYPE_ANOMALY_API_37).build(),
        ),
      )
    }.onFailure { error ->
      Log.w(TAG, "Unable to register Android 17 memory anomaly profiling", error)
    }
  }
}

internal fun recentMemoryProfilingArtifactNames(
  application: Application,
  exitTimestampMillis: Long,
  lookbackMillis: Long = 10L * 60L * 1000L,
): List<String> {
  val profilingDirectory = File(application.filesDir, "profiling")
  val earliest = exitTimestampMillis - lookbackMillis
  return profilingDirectory
    .listFiles()
    .orEmpty()
    .asSequence()
    .filter(File::isFile)
    .filter { file -> file.lastModified() in earliest..exitTimestampMillis }
    .sortedByDescending(File::lastModified)
    .map(File::getName)
    .filter { name -> name.all { it.isLetterOrDigit() || it in "._-" } }
    .take(3)
    .toList()
}
