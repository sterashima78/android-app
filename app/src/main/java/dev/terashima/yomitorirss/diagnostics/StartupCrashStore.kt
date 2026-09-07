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
      recordRecentProcessExit(application)
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

  fun recordRecentProcessExit(application: Application): Boolean = runCatching {
    val preferences = preferences(application)
    val lastSeen = preferences.getLong(LAST_EXIT_TIMESTAMP_KEY, 0L)
    val activityManager = application.getSystemService(ActivityManager::class.java)
    val unseen = activityManager
      .getHistoricalProcessExitReasons(application.packageName, 0, 0)
      .filter { it.timestamp > lastSeen }
    if (unseen.isEmpty()) return@runCatching false

    preferences.edit()
      .putLong(LAST_EXIT_TIMESTAMP_KEY, unseen.maxOf { it.timestamp })
      .commit()

    val reportableExit = unseen
      .filter { isAppOwnedProcessName(application.packageName, it.processName) }
      .filter {
        shouldReportProcessExit(
          packageName = application.packageName,
          processName = it.processName,
          reason = it.reason,
          description = it.description,
          importance = it.importance,
        )
      }
      .maxByOrNull { it.timestamp }
      ?: return@runCatching false
    val processName = reportableExit.processName ?: "unknown"

    val report = sanitizeCrashDetails(
      buildString {
        appendLine("Mosaic process exit report")
        appendLine("timestamp=${Instant.ofEpochMilli(reportableExit.timestamp)}")
        appendLine("version=${BuildConfig.VERSION_NAME}")
        appendLine("versionCode=${BuildConfig.VERSION_CODE}")
        appendLine("commit=${BuildConfig.GIT_COMMIT_SHA}")
        appendLine("sdk=${Build.VERSION.SDK_INT}")
        appendLine("release=${Build.VERSION.RELEASE}")
        appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("abis=${Build.SUPPORTED_ABIS.joinToString()}")
        appendLine("pid=${reportableExit.pid}")
        appendLine("process=$processName")
        appendLine("reason=${reportableExit.reason}")
        appendLine("reasonName=${processExitReasonName(reportableExit.reason)}")
        appendLine("status=${reportableExit.status}")
        appendLine("importance=${reportableExit.importance}")
        appendLine("importanceName=${processImportanceName(reportableExit.importance)}")
        appendLine("pssKb=${reportableExit.pss}")
        appendLine("rssKb=${reportableExit.rss}")
        processStateSummary(reportableExit)?.let { processState ->
          appendLine("processState=$processState")
        }
        reportableExit.description?.takeIf(String::isNotBlank)?.let { description ->
          appendLine("description=$description")
        }
        recentMemoryProfilingArtifactNames(application, reportableExit.timestamp)
          .takeIf { it.isNotEmpty() }
          ?.let { artifacts ->
            appendLine("profilingArtifacts=${artifacts.joinToString()}")
          }
        LocalAiMemoryDiagnostics.recentInferenceReport(
          context = application,
          pid = reportableExit.pid,
          processName = processName,
          untilTimestamp = reportableExit.timestamp,
        )?.let { diagnostics ->
          appendLine()
          appendLine("localAiMemoryDiagnostics:")
          append(diagnostics)
        }
        if (isLocalAiTextProcessName(application.packageName, processName)) {
          LocalAiTextProcessDiagnostics.recentProcessReport(
            context = application,
            pid = reportableExit.pid,
            untilTimestamp = reportableExit.timestamp,
          )?.let { diagnostics ->
            appendLine()
            appendLine("localAiTextProcessDiagnostics:")
            append(diagnostics)
          }
        }
      },
    )
    preferences.edit().putString(REPORT_KEY, report).commit()
    true
  }.getOrDefault(false)

  private fun preferences(context: Context) =
    context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}

internal fun isAppOwnedProcessName(packageName: String, processName: String?): Boolean =
  processName == packageName || processName?.startsWith("$packageName:") == true

internal fun isLocalAiTextProcessName(packageName: String, processName: String?): Boolean =
  processName == "$packageName:local_ai_text"

internal fun isGodotProcessName(packageName: String, processName: String?): Boolean =
  processName == "$packageName:godot"

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

internal fun shouldReportGodotProcessExit(
  packageName: String,
  processName: String?,
  reason: Int,
): Boolean =
  isGodotProcessName(packageName, processName) &&
    when (reason) {
      ApplicationExitInfo.REASON_CRASH,
      ApplicationExitInfo.REASON_CRASH_NATIVE,
      ApplicationExitInfo.REASON_ANR,
      ApplicationExitInfo.REASON_INITIALIZATION_FAILURE,
      -> true

      else -> false
    }

internal fun shouldReportProcessExit(
  packageName: String,
  processName: String?,
  reason: Int,
  description: String?,
  importance: Int,
): Boolean =
  shouldReportMemoryProcessExit(reason, description, importance) ||
    shouldReportGodotProcessExit(packageName, processName, reason)

internal fun processExitReasonName(reason: Int): String = when (reason) {
  ApplicationExitInfo.REASON_UNKNOWN -> "UNKNOWN"
  ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
  ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
  ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
  ApplicationExitInfo.REASON_CRASH -> "CRASH"
  ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
  ApplicationExitInfo.REASON_ANR -> "ANR"
  ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
  ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
  ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
  ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
  ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
  ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
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
