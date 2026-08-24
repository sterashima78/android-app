package dev.terashima.yomitorirss

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import dev.terashima.yomitorirss.core.airuntime.LocalAiMemoryDiagnostics
import java.time.Instant

object StartupCrashStore {
  private const val PREFERENCES_NAME = "startup_crash_diagnostics"
  private const val REPORT_KEY = "last_crash_report"
  private const val LAST_EXIT_TIMESTAMP_KEY = "last_process_exit_timestamp"

  @Volatile
  private var installed = false

  fun install(application: Application) {
    if (installed) return
    synchronized(this) {
      if (installed) return
      recordPreviousMemoryExit(application)
      val previous = Thread.getDefaultUncaughtExceptionHandler()
      Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        record(application, thread.name, throwable)
        previous?.uncaughtException(thread, throwable)
      }
      installed = true
    }
  }

  fun record(context: Context, threadName: String, throwable: Throwable) {
    runCatching {
      val report = sanitizeCrashDetails(
        buildString {
          appendLine("Mosaic crash report")
          appendLine("timestamp=${Instant.now()}")
          appendLine("version=${BuildConfig.VERSION_NAME}")
          appendLine("versionCode=${BuildConfig.VERSION_CODE}")
          appendLine("commit=${BuildConfig.GIT_COMMIT_SHA}")
          appendLine("sdk=${Build.VERSION.SDK_INT}")
          appendLine("release=${Build.VERSION.RELEASE}")
          appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
          appendLine("abis=${Build.SUPPORTED_ABIS.joinToString()}")
          appendLine("thread=$threadName")
          appendLine()
          append(throwable.stackTraceToString())
        },
      )
      preferences(context).edit().putString(REPORT_KEY, report).commit()
    }
  }

  fun peek(context: Context): String? =
    preferences(context).getString(REPORT_KEY, null)?.takeIf(String::isNotBlank)

  fun clear(context: Context) {
    preferences(context).edit().remove(REPORT_KEY).commit()
  }

  private fun recordPreviousMemoryExit(application: Application) {
    runCatching {
      val preferences = preferences(application)
      val lastSeen = preferences.getLong(LAST_EXIT_TIMESTAMP_KEY, 0L)
      val activityManager = application.getSystemService(ActivityManager::class.java)
      val unseen = activityManager
        .getHistoricalProcessExitReasons(application.packageName, 0, 0)
        .filter { it.timestamp > lastSeen }
      if (unseen.isEmpty()) return@runCatching

      preferences.edit()
        .putLong(LAST_EXIT_TIMESTAMP_KEY, unseen.maxOf { it.timestamp })
        .commit()

      val memoryExit = unseen
        .filter { isMemoryRelatedProcessExit(it.reason, it.description) }
        .maxByOrNull { it.timestamp }
        ?: return@runCatching
      val processName = memoryExit.processName ?: "unknown"

      val report = sanitizeCrashDetails(
        buildString {
          appendLine("Mosaic process exit report")
          appendLine("timestamp=${Instant.ofEpochMilli(memoryExit.timestamp)}")
          appendLine("version=${BuildConfig.VERSION_NAME}")
          appendLine("versionCode=${BuildConfig.VERSION_CODE}")
          appendLine("commit=${BuildConfig.GIT_COMMIT_SHA}")
          appendLine("sdk=${Build.VERSION.SDK_INT}")
          appendLine("release=${Build.VERSION.RELEASE}")
          appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
          appendLine("abis=${Build.SUPPORTED_ABIS.joinToString()}")
          appendLine("pid=${memoryExit.pid}")
          appendLine("process=$processName")
          appendLine("reason=${memoryExit.reason}")
          appendLine("status=${memoryExit.status}")
          appendLine("importance=${memoryExit.importance}")
          appendLine("pssKb=${memoryExit.pss}")
          appendLine("rssKb=${memoryExit.rss}")
          memoryExit.description?.takeIf(String::isNotBlank)?.let { description ->
            appendLine("description=$description")
          }
          recentMemoryProfilingArtifactNames(application, memoryExit.timestamp)
            .takeIf(List<String>::isNotEmpty)
            ?.let { artifacts ->
              appendLine("profilingArtifacts=${artifacts.joinToString()}")
            }
          LocalAiMemoryDiagnostics.recentInferenceReport(
            context = application,
            pid = memoryExit.pid,
            processName = processName,
            untilTimestamp = memoryExit.timestamp,
          )?.let { diagnostics ->
            appendLine()
            appendLine("localAiMemoryDiagnostics:")
            append(diagnostics)
          }
        },
      )
      preferences.edit().putString(REPORT_KEY, report).commit()
    }
  }

  private fun preferences(context: Context) =
    context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}

internal fun isMemoryRelatedProcessExit(reason: Int, description: String?): Boolean =
  reason == ApplicationExitInfo.REASON_LOW_MEMORY ||
    description?.contains("MemoryLimiter", ignoreCase = true) == true
