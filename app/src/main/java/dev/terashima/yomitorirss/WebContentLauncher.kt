package dev.terashima.yomitorirss

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

internal data class WebCustomTabRequest(
  val customTabsIntent: CustomTabsIntent,
  val uri: Uri,
)

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
  return try {
    request.customTabsIntent.launchUrl(this, request.uri)
    true
  } catch (_: ActivityNotFoundException) {
    false
  } catch (_: SecurityException) {
    false
  }
}
