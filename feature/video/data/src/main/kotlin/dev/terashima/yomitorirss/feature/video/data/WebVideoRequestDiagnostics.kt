package dev.terashima.yomitorirss.feature.video.data

import dev.terashima.yomitorirss.feature.video.WebVideoPlaybackDiagnostics
import dev.terashima.yomitorirss.feature.video.WebVideoSecFetchSite
import java.net.URI

internal data class ObservedWebVideoRequestDiagnostics(
  val streamRequestObserved: Boolean = false,
  val refererObserved: Boolean = false,
  val refererHasPathOrQuery: Boolean = false,
  val originObserved: Boolean = false,
  val originMatchesReferrerOrigin: Boolean? = null,
  val secFetchSite: WebVideoSecFetchSite? = null,
)

internal class WebVideoRequestDiagnosticsCapture(
  private val maxEntries: Int = 64,
) {
  private val entries = mutableListOf<Pair<String, ObservedWebVideoRequestDiagnostics>>()

  init {
    require(maxEntries > 0)
  }

  @Synchronized
  fun record(requestUrl: String, requestHeaders: Map<String, String>) {
    val safeRequestUrl = validPlaybackRequestUrl(requestUrl) ?: return
    val referer = headerValue(requestHeaders, "Referer")
    val origin = headerValue(requestHeaders, "Origin")
    val snapshot = ObservedWebVideoRequestDiagnostics(
      streamRequestObserved = true,
      refererObserved = !referer.isNullOrBlank(),
      refererHasPathOrQuery = referer?.let(::referrerHasPathOrQuery) ?: false,
      originObserved = !origin.isNullOrBlank(),
      originMatchesReferrerOrigin = originMatchesReferrer(origin, referer),
      secFetchSite = parseSecFetchSite(headerValue(requestHeaders, "Sec-Fetch-Site")),
    )
    entries.removeAll { (url, _) -> samePlaybackRequestUrl(url, safeRequestUrl) }
    entries += safeRequestUrl to snapshot
    while (entries.size > maxEntries) entries.removeAt(0)
  }

  @Synchronized
  fun diagnosticsFor(requestUrl: String): ObservedWebVideoRequestDiagnostics {
    val safeRequestUrl = validPlaybackRequestUrl(requestUrl)
      ?: return ObservedWebVideoRequestDiagnostics()
    return entries.lastOrNull { (url, _) -> samePlaybackRequestUrl(url, safeRequestUrl) }
      ?.second
      ?: ObservedWebVideoRequestDiagnostics()
  }

  @Synchronized
  fun clear() {
    entries.clear()
  }
}

internal fun webVideoPlaybackDiagnostics(
  cookieSharingEnabled: Boolean,
  cookieInterceptSupported: Boolean,
  observed: ObservedWebVideoRequestDiagnostics,
  streamRequestCookieObserved: Boolean,
  profileCookieAvailable: Boolean,
): WebVideoPlaybackDiagnostics = WebVideoPlaybackDiagnostics(
  cookieSharingEnabled = cookieSharingEnabled,
  cookieInterceptSupported = cookieInterceptSupported,
  streamRequestObserved = observed.streamRequestObserved,
  streamRequestCookieObserved = streamRequestCookieObserved,
  profileCookieAvailable = profileCookieAvailable,
  streamRequestRefererObserved = observed.refererObserved,
  streamRequestRefererHasPathOrQuery = observed.refererHasPathOrQuery,
  streamRequestOriginObserved = observed.originObserved,
  streamRequestOriginMatchesReferrerOrigin = observed.originMatchesReferrerOrigin,
  secFetchSite = observed.secFetchSite,
)

private fun headerValue(headers: Map<String, String>, name: String): String? =
  headers.entries.firstOrNull { (headerName, _) -> headerName.equals(name, ignoreCase = true) }?.value

private fun referrerHasPathOrQuery(referrer: String): Boolean = runCatching {
  val uri = URI(referrer)
  val path = uri.rawPath.orEmpty()
  path.isNotEmpty() && path != "/" || !uri.rawQuery.isNullOrEmpty()
}.getOrDefault(false)

private fun originMatchesReferrer(origin: String?, referrer: String?): Boolean? {
  if (origin.isNullOrBlank() || referrer.isNullOrBlank()) return null
  val originValue = normalizedOrigin(origin) ?: return false
  val referrerValue = normalizedOrigin(referrer) ?: return false
  return originValue == referrerValue
}

private fun normalizedOrigin(value: String): String? = runCatching {
  val uri = URI(value)
  val scheme = uri.scheme?.lowercase()
  require((scheme == "https" || scheme == "http") && !uri.host.isNullOrBlank())
  URI(scheme, null, uri.host.lowercase(), uri.port, null, null, null).toString()
}.getOrNull()

private fun parseSecFetchSite(value: String?): WebVideoSecFetchSite? = when (value?.lowercase()) {
  "same-origin" -> WebVideoSecFetchSite.SAME_ORIGIN
  "same-site" -> WebVideoSecFetchSite.SAME_SITE
  "cross-site" -> WebVideoSecFetchSite.CROSS_SITE
  "none" -> WebVideoSecFetchSite.NONE
  null, "" -> null
  else -> WebVideoSecFetchSite.OTHER
}
