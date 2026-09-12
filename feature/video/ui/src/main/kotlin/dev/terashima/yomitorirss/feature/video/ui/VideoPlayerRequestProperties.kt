package dev.terashima.yomitorirss.feature.video.ui

import dev.terashima.yomitorirss.feature.video.VideoPlaybackCookieProvider
import dev.terashima.yomitorirss.feature.video.VideoPlaybackTarget
import java.net.URI

internal fun webStreamRequestProperties(target: VideoPlaybackTarget.Stream): Map<String, String> = buildMap {
  val referrerUrl = target.referrerUrl?.takeIf(String::isNotBlank) ?: return@buildMap
  put("Referer", referrerUrl)
  webStreamOriginHeaderValue(referrerUrl)?.let { put("Origin", it) }
}

internal fun webStreamCookieRequestProperties(
  requestUrl: String,
  cookieProvider: VideoPlaybackCookieProvider,
): Map<String, String> {
  val cookie = runCatching { cookieProvider.cookieHeaderFor(requestUrl) }
    .getOrNull()
    ?.takeIf(String::isNotBlank)
    ?: return emptyMap()
  return mapOf("Cookie" to cookie)
}

internal fun webStreamOriginHeaderValue(referrerUrl: String): String? = runCatching {
  val uri = URI(referrerUrl)
  val scheme = uri.scheme?.lowercase()
  require((scheme == "https" || scheme == "http") && !uri.host.isNullOrBlank())
  URI(scheme, null, uri.host, uri.port, null, null, null).toString()
}.getOrNull()
