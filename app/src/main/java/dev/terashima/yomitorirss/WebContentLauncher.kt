package dev.terashima.yomitorirss

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.browser.customtabs.CustomTabsIntent

private const val CUSTOM_TAB_TRANSITION_WINDOW_MILLIS = 10_000L

internal data class WebCustomTabRequest(
  val customTabsIntent: CustomTabsIntent,
  val uri: Uri,
)

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

internal fun buildWebCustomTabRequest(url: String): WebCustomTabRequest? {
  val uri = Uri.parse(url.trim())
  val scheme = uri.scheme?.lowercase()
  if (scheme != "http" && scheme != "https") return null

  return WebCustomTabRequest(
    customTabsIntent = CustomTabsIntent.Builder().build(),
    uri = uri,
  )
}

internal fun Context.openWebContentInCustomTab(url: String): Boolean {
  val request = buildWebCustomTabRequest(url) ?: return false
  appLockExternalTransitionTracker.onCustomTabLaunchStarted()
  return try {
    request.customTabsIntent.launchUrl(this, request.uri)
    true
  } catch (_: ActivityNotFoundException) {
    appLockExternalTransitionTracker.onCustomTabLaunchFailed()
    false
  } catch (_: SecurityException) {
    appLockExternalTransitionTracker.onCustomTabLaunchFailed()
    false
  }
}
