package dev.terashima.yomitorirss.security

import android.os.SystemClock

private const val CUSTOM_TAB_TRANSITION_WINDOW_MILLIS = 10_000L

internal class AppLockExternalTransitionTracker(
  private val elapsedRealtimeMillis: () -> Long,
) {
  private var pendingCustomTabLaunchAtMillis: Long? = null

  fun onCustomTabLaunchStarted() {
    pendingCustomTabLaunchAtMillis = elapsedRealtimeMillis()
  }

  fun onCustomTabLaunchFailed() {
    pendingCustomTabLaunchAtMillis = null
  }

  fun shouldLockOnStop(): Boolean {
    val launchAtMillis = pendingCustomTabLaunchAtMillis ?: return true
    pendingCustomTabLaunchAtMillis = null
    val elapsedMillis = elapsedRealtimeMillis() - launchAtMillis
    return elapsedMillis !in 0..CUSTOM_TAB_TRANSITION_WINDOW_MILLIS
  }
}

internal val appLockExternalTransitionTracker =
  AppLockExternalTransitionTracker(SystemClock::elapsedRealtime)
