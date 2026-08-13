package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.core.network.HttpRequest
import java.io.IOException
import java.net.URI
import java.util.Locale

internal class AudibleCoverClient(
  private val httpClient: HttpClient = HttpClient.create(),
) {
  suspend fun lookup(sourceId: String): CoverLookupResult {
    val asin = sourceId.trim().uppercase(Locale.ROOT)
      .takeIf(AUDIBLE_ASIN::matches)
      ?: return CoverLookupResult(CoverLookupStatus.NOT_FOUND)

    val response = httpClient.execute(
      HttpRequest(
        url = "$AUDIBLE_PRODUCT_BASE_URL/$asin",
        headers = mapOf(
          "Accept" to "text/html,application/xhtml+xml",
          "Accept-Language" to "ja-JP,ja;q=0.9",
        ),
      ),
    )

    if (response.statusCode == 404 || response.statusCode == 410) {
      return CoverLookupResult(
        status = CoverLookupStatus.NOT_FOUND,
        matchedIdentifier = "ASIN:$asin",
      )
    }
    if (!response.isSuccessful) {
      throw IOException("Audible 商品ページの取得に失敗しました (${response.statusCode})")
    }
    if (!isAudibleProductPage(response.finalUrl)) {
      throw IOException("Audible 商品ページ以外へリダイレクトされました")
    }
    if (response.body.size > MAX_PRODUCT_PAGE_BYTES) {
      throw IOException("Audible 商品ページが大きすぎます")
    }

    val thumbnailUrl = extractAudibleCoverUrl(response.body.toString(Charsets.UTF_8))
      ?: return CoverLookupResult(
        status = CoverLookupStatus.NOT_FOUND,
        matchedIdentifier = "ASIN:$asin",
      )

    return CoverLookupResult(
      status = CoverLookupStatus.FOUND,
      thumbnailUrl = thumbnailUrl,
      matchedIdentifier = "ASIN:$asin",
    )
  }
}

internal fun extractAudibleCoverUrl(html: String): String? = META_TAG.findAll(html)
  .mapNotNull { match -> metaAttributes(match.value) }
  .firstNotNullOfOrNull { attributes ->
    val key = (attributes["property"] ?: attributes["name"])
      ?.lowercase(Locale.ROOT)
      ?: return@firstNotNullOfOrNull null
    if (key !in COVER_META_KEYS) return@firstNotNullOfOrNull null

    attributes["content"]
      ?.decodeHtmlEntities()
      ?.trim()
      ?.takeIf(::isTrustedCoverUrl)
  }

private fun metaAttributes(tag: String): Map<String, String> {
  return ATTRIBUTE.findAll(tag).associate { match ->
    val name = match.groupValues[1].lowercase(Locale.ROOT)
    val value = match.groupValues.drop(2).firstOrNull(String::isNotEmpty).orEmpty()
    name to value
  }
}

private fun isAudibleProductPage(url: String): Boolean {
  val uri = runCatching { URI(url) }.getOrNull() ?: return false
  if (!uri.scheme.equals("https", ignoreCase = true)) return false
  val host = uri.host?.lowercase(Locale.ROOT) ?: return false
  return host == "audible.co.jp" || host.endsWith(".audible.co.jp")
}

private fun isTrustedCoverUrl(url: String): Boolean {
  val uri = runCatching { URI(url) }.getOrNull() ?: return false
  if (!uri.scheme.equals("https", ignoreCase = true)) return false
  val host = uri.host?.lowercase(Locale.ROOT) ?: return false
  return TRUSTED_IMAGE_HOST_SUFFIXES.any { suffix ->
    host == suffix || host.endsWith(".$suffix")
  }
}

private fun String.decodeHtmlEntities(): String =
  replace("&amp;", "&")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&apos;", "'")

private const val AUDIBLE_PRODUCT_BASE_URL = "https://www.audible.co.jp/pd"
private const val MAX_PRODUCT_PAGE_BYTES = 5 * 1024 * 1024

private val AUDIBLE_ASIN = Regex("^[A-Z0-9]{10}$")
private val META_TAG = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE)
private val ATTRIBUTE = Regex(
  """([A-Za-z_:][-A-Za-z0-9_:.]*)\s*=\s*(?:\"([^\"]*)\"|'([^']*)'|([^\s\"'=<>`]+))""",
)
private val COVER_META_KEYS = setOf("og:image", "twitter:image", "twitter:image:src")
private val TRUSTED_IMAGE_HOST_SUFFIXES = setOf(
  "media-amazon.com",
  "ssl-images-amazon.com",
  "audible.co.jp",
)
