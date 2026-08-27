package dev.terashima.yomitorirss.diagnostics

import android.annotation.SuppressLint
import android.app.Application
import android.os.Build
import android.os.ProfilingManager
import android.os.ProfilingTrigger
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.File
import java.util.concurrent.Executor
import java.util.function.Consumer

/**
 * Registers Android 17's anomaly profiling trigger without raising the project's compile/target SDK
 * baseline. API-37 types and constants stay behind a runtime guard and a separately loaded object.
 */
internal object Android17MemoryAnomalyProfiler {
  fun install(application: Application) {
    if (Build.VERSION.SDK_INT < 37) return
    Api37.install(application)
  }

  @RequiresApi(37)
  private object Api37 {
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

    @SuppressLint("WrongConstant")
    fun install(application: Application) {
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
}

internal fun recentMemoryProfilingArtifactNames(
  application: Application,
  exitTimestampMillis: Long,
  lookbackMillis: Long = 10L * 60L * 1000L,
): List<String> = recentMemoryProfilingArtifactNames(
  profilingDirectory = File(application.filesDir, "profiling"),
  exitTimestampMillis = exitTimestampMillis,
  lookbackMillis = lookbackMillis,
)

internal fun recentMemoryProfilingArtifactNames(
  profilingDirectory: File,
  exitTimestampMillis: Long,
  lookbackMillis: Long,
): List<String> {
  require(lookbackMillis >= 0L) { "lookbackMillis must not be negative" }
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
