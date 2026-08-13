package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.core.network.HttpRequest
import java.io.IOException
import java.net.URI
import java.util.Locale
import org.json.JSONObject

internal enum class KindleCoverProvider(val storageValue: String) {
  AMAZON_PRODUCT_PAGE_OGP("AMAZON_PRODUCT_PAGE_OGP"),
  AMAZON_PRODUCT_PAGE_IMAGE("AMAZON_PRODUCT_PAGE_IMAGE"),
  OPEN_LIBRARY("OPEN_LIBRARY"),
}

internal data class KindleCoverLookupResult(
  val lookup: CoverLookupResult,
  val provider: KindleCoverProvider,
)

internal class KindleAmazonCoverClient(
  private val httpClient: HttpClient = HttpClient.create(),
) {
  suspend fun lookup(sourceId: String): KindleCoverLookupResult {
    val asin = sourceId.trim().uppercase(Locale.ROOT)
      .takeIf(KINDLE_ASIN::matches)
      ?: return notFound()

    val response = httpClient.execute(
      HttpRequest(
        url = "$AMAZON_PRODUCT_BASE_URL/$asin",
        headers = mapOf(
          "Accept" to "text/html,application/xhtml+xml",
          "Accept-Language" to "ja-JP,ja;q=0.9",
        ),
      ),
    )

    if (response.statusCode == 404 || response.statusCode == 410) return notFound(asin)
    if (!response.isSuccessful) {
      throw IOException("Amazon 商品ページの取得に失敗しました (${response.statusCode})")
    }
    if (!isAmazonProductPageForAsin(response.finalUrl, asin)) {
      throw IOException("Amazon 商品ページ以外へリダイレクトされました")
    }
    if (response.body.size > MAX_PRODUCT_PAGE_BYTES) {
      throw IOException("Amazon 商品ページが大きすぎます")
    }

    val html = response.body.toString(Charsets.UTF_8)
    extractAmazonOgCoverUrl(html)?.let { imageUrl ->
      return found(asin, imageUrl, KindleCoverProvider.AMAZON_PRODUCT_PAGE_OGP)
    }
    extractAmazonProductImageUrl(html)?.let { imageUrl ->
      return found(asin, imageUrl, KindleCoverProvider.AMAZON_PRODUCT_PAGE_IMAGE)
    }
    return notFound(asin)
  }

  private fun found(
    asin: String,
    imageUrl: String,
    provider: KindleCoverProvider,
  ): KindleCoverLookupResult = KindleCoverLookupResult(
    lookup = CoverLookupResult(
      status = CoverLookupStatus.FOUND,
      thumbnailUrl = imageUrl,
      matchedIdentifier = "ASIN:$asin",
    ),
    provider = provider,
  )

  private fun notFound(asin: String? = null): KindleCoverLookupResult = KindleCoverLookupResult(
    lookup = CoverLookupResult(
      status = CoverLookupStatus.NOT_FOUND,
      matchedIdentifier = asin?.let { "ASIN:$it" },
    ),
    provider = KindleCoverProvider.AMAZON_PRODUCT_PAGE_OGP,
  )
}

internal fun extractAmazonOgCoverUrl(html: String): String? = META_TAG.findAll(html)
  .map { match -> htmlAttributes(match.value) }
  .firstNotNullOfOrNull { attributes ->
    val key = (attributes["property"] ?: attributes["name"])
      ?.lowercase(Locale.ROOT)
      ?: return@firstNotNullOfOrNull null
    if (key !in COVER_META_KEYS) return@firstNotNullOfOrNull null
    attributes["content"]
      ?.decodeHtmlEntities()
      ?.trim()
      ?.takeIf(::isTrustedAmazonImageUrl)
  }

internal fun extractAmazonProductImageUrl(html: String): String? = IMAGE_TAG.findAll(html)
  .map { match -> htmlAttributes(match.value) }
  .filter { attributes -> attributes["id"]?.lowercase(Locale.ROOT) in PRODUCT_IMAGE_IDS }
  .firstNotNullOfOrNull { attributes ->
    HIGH_RES_IMAGE_URL_ATTRIBUTES.firstNotNullOfOrNull { attribute ->
      attributes[attribute]
        ?.decodeHtmlEntities()
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.takeIf(::isTrustedAmazonImageUrl)
    } ?: attributes["data-a-dynamic-image"]
      ?.decodeHtmlEntities()
      ?.let(::largestDynamicImageUrl)
      ?: attributes["src"]
        ?.decodeHtmlEntities()
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.takeIf(::isTrustedAmazonImageUrl)
  }

internal fun isAmazonProductPageForAsin(url: String, asin: String): Boolean {
  val uri = runCatching { URI(url) }.getOrNull() ?: return false
  if (!uri.scheme.equals("https", ignoreCase = true)) return false
  val host = uri.host?.lowercase(Locale.ROOT) ?: return false
  if (host != "amazon.co.jp" && !host.endsWith(".amazon.co.jp")) return false

  val escapedAsin = Regex.escape(asin.uppercase(Locale.ROOT))
  return Regex(
    "/(?:dp|gp/product|gp/aw/d)/$escapedAsin(?:/|$)",
    RegexOption.IGNORE_CASE,
  ).containsMatchIn(uri.path.orEmpty())
}

private fun largestDynamicImageUrl(value: String): String? {
  val root = runCatching { JSONObject(value) }.getOrNull() ?: return null
  val candidates = buildList {
    val keys = root.keys()
    while (keys.hasNext()) {
      val url = keys.next()
      if (!isTrustedAmazonImageUrl(url)) continue
      val size = root.optJSONArray(url) ?: continue
      val width = size.optLong(0).takeIf { it > 0 } ?: continue
      val height = size.optLong(1).takeIf { it > 0 } ?: continue
      add(DynamicImage(url = url, area = width * height))
    }
  }
  return candidates.maxByOrNull(DynamicImage::area)?.url
}

private fun htmlAttributes(tag: String): Map<String, String> = ATTRIBUTE.findAll(tag).associate { match ->
  val name = match.groupValues[1].lowercase(Locale.ROOT)
  val value = match.groupValues.drop(2).firstOrNull(String::isNotEmpty).orEmpty()
  name to value
}

private fun isTrustedAmazonImageUrl(url: String): Boolean {
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
    .replace("&#34;", "\"")
    .replace("&#39;", "'")
    .replace("&#x27;", "'", ignoreCase = true)
    .replace("&apos;", "'")

private data class DynamicImage(
  val url: String,
  val area: Long,
)

private const val AMAZON_PRODUCT_BASE_URL = "https://www.amazon.co.jp/dp"
private const val MAX_PRODUCT_PAGE_BYTES = 8 * 1024 * 1024

private val KINDLE_ASIN = Regex("^[A-Z0-9]{10}$")
private val META_TAG = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE)
private val IMAGE_TAG = Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE)
private val ATTRIBUTE = Regex(
  """([A-Za-z_:][-A-Za-z0-9_:.]*)\s*=\s*(?:\"([^\"]*)\"|'([^']*)'|([^\s\"'=<>`]+))""",
)
private val COVER_META_KEYS = setOf("og:image", "twitter:image", "twitter:image:src")
private val PRODUCT_IMAGE_IDS = setOf(
  "landingimage",
  "imgblkfront",
  "ebooksimgblkfront",
)
private val HIGH_RES_IMAGE_URL_ATTRIBUTES = listOf(
  "data-old-hires",
  "data-a-hires",
)
private val TRUSTED_IMAGE_HOST_SUFFIXES = setOf(
  "media-amazon.com",
  "ssl-images-amazon.com",
  "images.amazon.com",
)
