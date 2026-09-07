package dev.terashima.yomitorirss.feature.video.data

import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.core.network.HttpRequest
import java.net.URI
import java.util.Locale
import org.jsoup.Jsoup

internal data class StaticWebVideoMetadata(
  val title: String? = null,
  val thumbnailUrl: String? = null,
)

internal class WebVideoMetadataClient(
  private val httpClient: HttpClient,
) {
  suspend fun fetch(url: String): StaticWebVideoMetadata {
    val response = httpClient.execute(
      HttpRequest(
        url = url,
        maxResponseBytes = MAX_METADATA_BYTES,
      ),
    )
    if (!response.isSuccessful) return StaticWebVideoMetadata()

    val finalUrl = response.finalUrl.ifBlank { url }
    val document = Jsoup.parse(response.body.toString(Charsets.UTF_8), finalUrl)
    val title = document.selectFirst("meta[property=og:title]")
      ?.attr("content")
      ?.trim()
      ?.takeIf(String::isNotBlank)
      ?: document.selectFirst("meta[name=twitter:title]")
        ?.attr("content")
        ?.trim()
        ?.takeIf(String::isNotBlank)
      ?: document.title().trim().takeIf(String::isNotBlank)

    val thumbnailCandidate = document.selectFirst("meta[property=og:image]")?.attr("content")
      ?: document.selectFirst("meta[name=twitter:image]")?.attr("content")
    val thumbnail = thumbnailCandidate
      ?.trim()
      ?.takeIf(String::isNotBlank)
      ?.let { resolveWebVideoUrl(finalUrl, it, httpsOnly = true) }

    return StaticWebVideoMetadata(title = title, thumbnailUrl = thumbnail)
  }

  private companion object {
    const val MAX_METADATA_BYTES = 2L * 1024 * 1024
  }
}

internal fun normalizeWebVideoUrl(value: String): String {
  val uri = URI(value.trim())
  require(uri.scheme.equals("https", ignoreCase = true) || uri.scheme.equals("http", ignoreCase = true)) {
    "Web動画URLはHTTPまたはHTTPSで指定してください"
  }
  require(!uri.host.isNullOrBlank()) { "Web動画URLのホストが不正です" }
  return uri.normalize().toString()
}

internal fun resolveWebVideoUrl(
  baseUrl: String,
  candidate: String,
  httpsOnly: Boolean,
): String? = runCatching {
  val uri = URI(baseUrl).resolve(candidate)
  val scheme = uri.scheme?.lowercase(Locale.ROOT)
  val allowed = if (httpsOnly) scheme == "https" else scheme == "https" || scheme == "http"
  uri.takeIf { allowed && !it.host.isNullOrBlank() }?.toString()
}.getOrNull()
