package dev.terashima.yomitorirss.feature.game

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.util.Log

internal data class GodotProcessExit(
  val reason: Int,
  val status: Int,
  val timestamp: Long,
  val description: String?,
)

internal object GodotProcessExitDiagnostics {
  private const val TAG = "GodotSudoku"
  private const val MAX_EXIT_RECORDS = 16

  fun findLatestAfter(context: Context, launchedAtMillis: Long): GodotProcessExit? {
    val activityManager = context.getSystemService(ActivityManager::class.java) ?: return null
    val processName = "${context.packageName}:godot"
    return activityManager
      .getHistoricalProcessExitReasons(context.packageName, 0, MAX_EXIT_RECORDS)
      .asSequence()
      .filter { it.processName == processName && it.timestamp >= launchedAtMillis }
      .maxByOrNull(ApplicationExitInfo::getTimestamp)
      ?.let {
        GodotProcessExit(
          reason = it.reason,
          status = it.status,
          timestamp = it.timestamp,
          description = it.description,
        )
      }
  }

  fun log(exit: GodotProcessExit) {
    Log.e(
      TAG,
      "Godot process exited unexpectedly: reason=${reasonName(exit.reason)}, " +
        "status=${exit.status}, timestamp=${exit.timestamp}, description=${exit.description}",
    )
  }

  fun isCrash(exit: GodotProcessExit): Boolean = when (exit.reason) {
    ApplicationExitInfo.REASON_CRASH,
    ApplicationExitInfo.REASON_CRASH_NATIVE,
    ApplicationExitInfo.REASON_ANR,
    ApplicationExitInfo.REASON_SIGNALED,
    -> true

    else -> false
  }

  internal fun reasonName(reason: Int): String = when (reason) {
    ApplicationExitInfo.REASON_EXIT_SELF -> "exit_self"
    ApplicationExitInfo.REASON_SIGNALED -> "signaled"
    ApplicationExitInfo.REASON_LOW_MEMORY -> "low_memory"
    ApplicationExitInfo.REASON_CRASH -> "crash"
    ApplicationExitInfo.REASON_CRASH_NATIVE -> "crash_native"
    ApplicationExitInfo.REASON_ANR -> "anr"
    ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "initialization_failure"
    ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "permission_change"
    ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "excessive_resource_usage"
    ApplicationExitInfo.REASON_USER_REQUESTED -> "user_requested"
    ApplicationExitInfo.REASON_USER_STOPPED -> "user_stopped"
    ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "dependency_died"
    ApplicationExitInfo.REASON_OTHER -> "other"
    else -> "unknown($reason)"
  }
}
