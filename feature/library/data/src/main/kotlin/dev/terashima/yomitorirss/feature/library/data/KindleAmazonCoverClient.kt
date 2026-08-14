package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.core.network.HttpRequest
import dev.terashima.yomitorirss.core.network.HttpResponse
import java.net.URI
import java.util.Locale
import org.json.JSONObject

internal enum class KindleCoverProvider(val storageValue: String) {
  AMAZON_PRODUCT_PAGE_OGP("AMAZON_PRODUCT_PAGE_OGP"),
  AMAZON_PRODUCT_PAGE_IMAGE("AMAZON_PRODUCT_PAGE_IMAGE"),
  AMAZON_PRODUCT_PAGE_JSON_LD("AMAZON_PRODUCT_PAGE_JSON_LD"),
  AMAZON_PRODUCT_PAGE_IMAGE_SRC("AMAZON_PRODUCT_PAGE_IMAGE_SRC"),
  GOOGLE_BOOKS("GOOGLE_BOOKS"),
  OPEN_LIBRARY("OPEN_LIBRARY"),
}

internal data class KindleCoverLookupResult(
  val lookup: CoverLookupResult,
  val provider: KindleCoverProvider,
  val traceStep: CoverLookupTraceStep,
)

internal class KindleAmazonCoverClient(
  private val httpClient: HttpClient = HttpClient.create(),
) {
  suspend fun lookup(sourceId: String): KindleCoverLookupResult {
    val asin = sourceId.trim().uppercase(Locale.ROOT)
      .takeIf(KINDLE_ASIN::matches)
      ?: return notFound(null, "INVALID_ASIN")

    val response = httpClient.execute(
      HttpRequest(
        url = "$AMAZON_PRODUCT_BASE_URL/$asin",
        headers = mapOf(
          "Accept" to "text/html,application/xhtml+xml",
          "Accept-Language" to "ja-JP,ja;q=0.9",
        ),
      ),
    )

    if (response.statusCode == 404 || response.statusCode == 410) {
      return notFound(
        asin = asin,
        reason = "HTTP_NOT_FOUND",
        httpStatus = response.statusCode,
        responseBytes = response.body.size,
      )
    }
    if (!response.isSuccessful) {
      val retryable = isRetryableAmazonStatus(response.statusCode)
      throw CoverProviderIOException(
        "Amazon 商品ページの取得に失敗しました (${response.statusCode})",
        CoverLookupTraceStep(
          provider = AMAZON_PROVIDER,
          status = CoverLookupStatus.ERROR,
          reason = if (retryable) "HTTP_RETRYABLE" else "HTTP_ERROR",
          retryable = retryable,
          retryAfterSeconds = response.retryAfterSeconds(),
          httpStatus = response.statusCode,
          responseBytes = response.body.size,
        ),
      )
    }
    if (!isAmazonProductPageForAsin(response.finalUrl, asin)) {
      throw CoverProviderIOException(
        "Amazon 商品ページ以外へリダイレクトされました",
        baseStep(response.statusCode, response.body.size, "UNEXPECTED_REDIRECT"),
      )
    }
    val contentType = response.header("Content-Type")?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
    if (contentType != null && contentType !in AMAZON_HTML_CONTENT_TYPES) {
      throw CoverProviderIOException(
        "Amazon 商品ページが HTML ではありません ($contentType)",
        baseStep(
          response.statusCode,
          response.body.size,
          "INVALID_CONTENT_TYPE",
          mapOf("contentType" to contentType),
        ),
      )
    }
    if (response.body.size > MAX_PRODUCT_PAGE_BYTES) {
      throw CoverProviderIOException(
        "Amazon 商品ページが大きすぎます",
        baseStep(response.statusCode, response.body.size, "RESPONSE_TOO_LARGE"),
      )
    }

    val html = response.body.toString(Charsets.UTF_8)
    if (isAmazonChallengePage(html)) {
      throw CoverProviderIOException(
        "Amazon 商品ページでアクセス確認が要求されました",
        baseStep(response.statusCode, response.body.size, "CHALLENGE_PAGE"),
      )
    }

    val counts = amazonCoverCandidateCounts(html)
    val attributes = mapOf(
      "contentType" to (contentType ?: "unknown"),
      "finalUrlMatches" to "true",
      "asinPresent" to html.contains(asin, ignoreCase = true).toString(),
      "ogCandidates" to counts.og.toString(),
      "productImageCandidates" to counts.productImage.toString(),
      "jsonLdCandidates" to counts.jsonLd.toString(),
      "imageSrcCandidates" to counts.imageSrc.toString(),
    )

    extractAmazonOgCoverUrl(html)?.let { imageUrl ->
      return found(asin, imageUrl, KindleCoverProvider.AMAZON_PRODUCT_PAGE_OGP, response, attributes)
    }
    extractAmazonProductImageUrl(html)?.let { imageUrl ->
      return found(asin, imageUrl, KindleCoverProvider.AMAZON_PRODUCT_PAGE_IMAGE, response, attributes)
    }
    extractAmazonJsonLdCoverUrl(html)?.let { imageUrl ->
      return found(asin, imageUrl, KindleCoverProvider.AMAZON_PRODUCT_PAGE_JSON_LD, response, attributes)
    }
    extractAmazonImageSrcCoverUrl(html)?.let { imageUrl ->
      return found(asin, imageUrl, KindleCoverProvider.AMAZON_PRODUCT_PAGE_IMAGE_SRC, response, attributes)
    }
    return notFound(
      asin = asin,
      reason = "PRODUCT_PAGE_WITHOUT_COVER",
      httpStatus = response.statusCode,
      responseBytes = response.body.size,
      attributes = attributes,
    )
  }

  private fun found(
    asin: String,
    imageUrl: String,
    provider: KindleCoverProvider,
    response: HttpResponse,
    attributes: Map<String, String>,
  ): KindleCoverLookupResult = KindleCoverLookupResult(
    lookup = CoverLookupResult(
      status = CoverLookupStatus.FOUND,
      thumbnailUrl = imageUrl,
      matchedIdentifier = "ASIN:$asin",
    ),
    provider = provider,
    traceStep = CoverLookupTraceStep(
      provider = AMAZON_PROVIDER,
      status = CoverLookupStatus.FOUND,
      reason = provider.storageValue,
      httpStatus = response.statusCode,
      responseBytes = response.body.size,
      attributes = attributes,
    ),
  )

  private fun notFound(
    asin: String?,
    reason: String,
    httpStatus: Int? = null,
    responseBytes: Int? = null,
    attributes: Map<String, String> = emptyMap(),
  ): KindleCoverLookupResult = KindleCoverLookupResult(
    lookup = CoverLookupResult(
      status = CoverLookupStatus.NOT_FOUND,
      matchedIdentifier = asin?.let { "ASIN:$it" },
    ),
    provider = KindleCoverProvider.AMAZON_PRODUCT_PAGE_OGP,
    traceStep = CoverLookupTraceStep(
      provider = AMAZON_PROVIDER,
      status = CoverLookupStatus.NOT_FOUND,
      reason = reason,
      httpStatus = httpStatus,
      responseBytes = responseBytes,
      attributes = attributes,
    ),
  )

  private fun baseStep(
    httpStatus: Int,
    responseBytes: Int,
    reason: String,
    attributes: Map<String, String> = emptyMap(),
  ) = CoverLookupTraceStep(
    provider = AMAZON_PROVIDER,
    status = CoverLookupStatus.ERROR,
    reason = reason,
    retryable = true,
    httpStatus = httpStatus,
    responseBytes = responseBytes,
    attributes = attributes,
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

internal fun extractAmazonJsonLdCoverUrl(html: String): String? = JSON_LD_SCRIPT.findAll(html)
  .map { it.groupValues[1] }
  .flatMap { script -> JSON_LD_IMAGE.findAll(script).map { it.groupValues[1] } }
  .map { it.decodeJsonEscapes().decodeHtmlEntities().trim() }
  .firstOrNull(::isTrustedAmazonImageUrl)

internal fun extractAmazonImageSrcCoverUrl(html: String): String? = LINK_TAG.findAll(html)
  .map { match -> htmlAttributes(match.value) }
  .firstNotNullOfOrNull { attributes ->
    if (!attributes["rel"].orEmpty().equals("image_src", ignoreCase = true)) {
      return@firstNotNullOfOrNull null
    }
    attributes["href"]
      ?.decodeHtmlEntities()
      ?.trim()
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

internal fun isAmazonChallengePage(html: String): Boolean {
  val lowercaseHtml = html.lowercase(Locale.ROOT)
  return AMAZON_CHALLENGE_MARKERS.any(lowercaseHtml::contains)
}

internal fun isRetryableAmazonStatus(statusCode: Int): Boolean =
  statusCode == 408 || statusCode == 429 || statusCode in 500..599

private fun amazonCoverCandidateCounts(html: String): AmazonCoverCandidateCounts = AmazonCoverCandidateCounts(
  og = META_TAG.findAll(html).count { match ->
    val attrs = htmlAttributes(match.value)
    (attrs["property"] ?: attrs["name"])?.lowercase(Locale.ROOT) in COVER_META_KEYS
  },
  productImage = IMAGE_TAG.findAll(html).count { match ->
    htmlAttributes(match.value)["id"]?.lowercase(Locale.ROOT) in PRODUCT_IMAGE_IDS
  },
  jsonLd = JSON_LD_SCRIPT.findAll(html).sumOf { match -> JSON_LD_IMAGE.findAll(match.groupValues[1]).count() },
  imageSrc = LINK_TAG.findAll(html).count { match ->
    htmlAttributes(match.value)["rel"].orEmpty().equals("image_src", ignoreCase = true)
  },
)

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
  val trustedHost = TRUSTED_IMAGE_HOST_SUFFIXES.any { suffix ->
    host == suffix || host.endsWith(".$suffix")
  }
  return trustedHost && uri.path.orEmpty().contains("/images/I/", ignoreCase = true)
}

private fun HttpResponse.retryAfterSeconds(): Long? = headers.entries
  .firstOrNull { (name, _) -> name.equals("Retry-After", ignoreCase = true) }
  ?.value
  ?.firstOrNull()
  ?.trim()
  ?.toLongOrNull()

private fun String.decodeHtmlEntities(): String =
  replace("&amp;", "&")
    .replace("&quot;", "\"")
    .replace("&#34;", "\"")
    .replace("&#39;", "'")
    .replace("&#x27;", "'", ignoreCase = true)
    .replace("&apos;", "'")

private fun String.decodeJsonEscapes(): String =
  replace("\\/", "/")
    .replace("\\u0026", "&", ignoreCase = true)

private data class DynamicImage(
  val url: String,
  val area: Long,
)

private data class AmazonCoverCandidateCounts(
  val og: Int,
  val productImage: Int,
  val jsonLd: Int,
  val imageSrc: Int,
)

private const val AMAZON_PRODUCT_BASE_URL = "https://www.amazon.co.jp/dp"
private const val AMAZON_PROVIDER = "AMAZON_PRODUCT_PAGE"
private const val MAX_PRODUCT_PAGE_BYTES = 8 * 1024 * 1024

private val KINDLE_ASIN = Regex("^[A-Z0-9]{10}$")
private val META_TAG = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE)
private val IMAGE_TAG = Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE)
private val LINK_TAG = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE)
private val JSON_LD_SCRIPT = Regex(
  """<script\b[^>]*type\s*=\s*["']application/ld\+json["'][^>]*>(.*?)</script>""",
  setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val JSON_LD_IMAGE = Regex("""["']image["']\s*:\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
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
private val AMAZON_CHALLENGE_MARKERS = setOf(
  "validatecaptcha",
  "captchacharacters",
  "enter the characters you see below",
)
private val AMAZON_HTML_CONTENT_TYPES = setOf("text/html", "application/xhtml+xml")
