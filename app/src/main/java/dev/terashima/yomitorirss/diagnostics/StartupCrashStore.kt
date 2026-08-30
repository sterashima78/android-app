package dev.terashima.yomitorirss.diagnostics

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import dev.terashima.yomitorirss.BuildConfig
import dev.terashima.yomitorirss.core.airuntime.LocalAiMemoryDiagnostics
import dev.terashima.yomitorirss.core.airuntime.LocalAiTextProcessDiagnostics
import java.nio.charset.StandardCharsets
import java.time.Instant

internal const val ANDROID_17_REASON_MEMORY_LIMITER = 17

internal object StartupCrashStore {
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
        .filter { isAppOwnedProcessName(application.packageName, it.processName) }
        .filter { shouldReportMemoryProcessExit(it.reason, it.description, it.importance) }
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
          appendLine("reasonName=${processExitReasonName(memoryExit.reason)}")
          appendLine("status=${memoryExit.status}")
          appendLine("importance=${memoryExit.importance}")
          appendLine("importanceName=${processImportanceName(memoryExit.importance)}")
          appendLine("pssKb=${memoryExit.pss}")
          appendLine("rssKb=${memoryExit.rss}")
          processStateSummary(memoryExit)?.let { processState ->
            appendLine("processState=$processState")
          }
          memoryExit.description?.takeIf(String::isNotBlank)?.let { description ->
            appendLine("description=$description")
          }
          recentMemoryProfilingArtifactNames(application, memoryExit.timestamp)
            .takeIf { it.isNotEmpty() }
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
          if (isLocalAiTextProcessName(application.packageName, processName)) {
            LocalAiTextProcessDiagnostics.recentProcessReport(
              context = application,
              pid = memoryExit.pid,
              untilTimestamp = memoryExit.timestamp,
            )?.let { diagnostics ->
              appendLine()
              appendLine("localAiTextProcessDiagnostics:")
              append(diagnostics)
            }
          }
        },
      )
      preferences.edit().putString(REPORT_KEY, report).commit()
    }
  }

  private fun preferences(context: Context) =
    context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}

internal fun isAppOwnedProcessName(packageName: String, processName: String?): Boolean =
  processName == packageName || processName?.startsWith("$packageName:") == true

internal fun isLocalAiTextProcessName(packageName: String, processName: String?): Boolean =
  processName == "$packageName:local_ai_text"

internal fun isMemoryRelatedProcessExit(reason: Int, description: String?): Boolean =
  reason == ApplicationExitInfo.REASON_LOW_MEMORY ||
    reason == ANDROID_17_REASON_MEMORY_LIMITER ||
    description?.contains("MemoryLimiter", ignoreCase = true) == true

internal fun shouldReportMemoryProcessExit(
  reason: Int,
  description: String?,
  importance: Int,
): Boolean =
  isMemoryRelatedProcessExit(reason, description) &&
    !(
      reason == ApplicationExitInfo.REASON_LOW_MEMORY &&
        importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED
    )

internal fun processExitReasonName(reason: Int): String = when (reason) {
  ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
  ApplicationExitInfo.REASON_OTHER -> "OTHER"
  ANDROID_17_REASON_MEMORY_LIMITER -> "MEMORY_LIMITER"
  else -> "REASON_$reason"
}

internal fun processImportanceName(importance: Int): String = when (importance) {
  ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "FOREGROUND"
  ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> "FOREGROUND_SERVICE"
  ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "VISIBLE"
  ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "SERVICE"
  ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED -> "CACHED"
  else -> "IMPORTANCE_$importance"
}

private fun processStateSummary(exitInfo: ApplicationExitInfo): String? {
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
  return exitInfo.processStateSummary
    ?.let { bytes -> String(bytes, StandardCharsets.US_ASCII) }
    ?.takeIf(String::isNotBlank)
}
