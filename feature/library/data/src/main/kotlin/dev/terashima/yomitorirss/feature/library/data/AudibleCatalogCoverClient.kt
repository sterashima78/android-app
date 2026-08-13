package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.core.network.HttpRequest
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import org.json.JSONObject

internal enum class AudibleCoverProvider(val storageValue: String) {
  PRODUCT_PAGE("AUDIBLE_PRODUCT_PAGE"),
  CATALOG_API_ASIN("AUDIBLE_CATALOG_API_ASIN"),
  CATALOG_API_SEARCH("AUDIBLE_CATALOG_API_SEARCH"),
}

internal data class AudibleCoverLookupResult(
  val lookup: CoverLookupResult,
  val provider: AudibleCoverProvider,
)

internal class AudibleCatalogCoverClient(
  private val httpClient: HttpClient = HttpClient.create(),
) {
  suspend fun lookup(
    sourceId: String,
    title: String,
    authors: List<String>,
  ): AudibleCoverLookupResult {
    val asin = sourceId.trim().uppercase(Locale.ROOT).takeIf(AUDIBLE_ASIN::matches)
    var asinResult: CoverLookupResult? = null
    var asinFailure: IOException? = null

    if (asin != null) {
      try {
        asinResult = lookupByAsin(asin)
        if (asinResult.status == CoverLookupStatus.FOUND) {
          return AudibleCoverLookupResult(asinResult, AudibleCoverProvider.CATALOG_API_ASIN)
        }
      } catch (error: IOException) {
        asinFailure = error
      }
    }

    if (title.isBlank()) {
      asinFailure?.let { throw it }
      return AudibleCoverLookupResult(
        asinResult ?: CoverLookupResult(CoverLookupStatus.NOT_FOUND),
        AudibleCoverProvider.CATALOG_API_ASIN,
      )
    }

    return try {
      AudibleCoverLookupResult(
        lookupByTitle(title, authors),
        AudibleCoverProvider.CATALOG_API_SEARCH,
      )
    } catch (error: IOException) {
      throw asinFailure ?: error
    }
  }

  private suspend fun lookupByAsin(asin: String): CoverLookupResult {
    val response = httpClient.execute(
      HttpRequest(
        url = "$CATALOG_PRODUCTS_URL/$asin" +
          "?response_groups=media,product_desc,contributors&image_sizes=$IMAGE_SIZE",
        headers = headers(),
      ),
    )
    if (response.statusCode == 404 || response.statusCode == 410) {
      return CoverLookupResult(CoverLookupStatus.NOT_FOUND, matchedIdentifier = "ASIN:$asin")
    }
    validateResponse(response.statusCode, response.isSuccessful, response.finalUrl, response.body.size)

    val product = parseJson(response.body).optJSONObject("product")?.toCandidate()
      ?: return CoverLookupResult(CoverLookupStatus.NOT_FOUND, matchedIdentifier = "ASIN:$asin")
    if (!product.asin.equals(asin, ignoreCase = true)) {
      throw IOException("Audible Catalog API が要求した ASIN と異なる商品を返しました")
    }
    return product.thumbnailUrl?.let { imageUrl ->
      CoverLookupResult(
        status = CoverLookupStatus.FOUND,
        thumbnailUrl = imageUrl,
        matchedIdentifier = "ASIN:$asin",
      )
    } ?: CoverLookupResult(CoverLookupStatus.NOT_FOUND, matchedIdentifier = "ASIN:$asin")
  }

  private suspend fun lookupByTitle(
    title: String,
    authors: List<String>,
  ): CoverLookupResult {
    val cleanTitle = title.trim()
    if (cleanTitle.isEmpty()) return CoverLookupResult(CoverLookupStatus.NOT_FOUND)
    val author = authors.firstOrNull()?.trim()?.takeIf(String::isNotEmpty)
    val url = buildString {
      append(CATALOG_PRODUCTS_URL)
      append("?title=")
      append(cleanTitle.urlEncode())
      author?.let {
        append("&author=")
        append(it.urlEncode())
      }
      append("&num_results=$SEARCH_LIMIT")
      append("&products_sort_by=Relevance")
      append("&response_groups=media,product_desc,contributors")
      append("&image_sizes=$IMAGE_SIZE")
    }
    val response = httpClient.execute(HttpRequest(url = url, headers = headers()))
    if (response.statusCode == 404 || response.statusCode == 410) {
      return CoverLookupResult(CoverLookupStatus.NOT_FOUND)
    }
    validateResponse(response.statusCode, response.isSuccessful, response.finalUrl, response.body.size)

    val products = parseJson(response.body).optJSONArray("products")
      ?: return CoverLookupResult(CoverLookupStatus.NOT_FOUND)
    val candidates = buildList {
      for (index in 0 until products.length()) {
        products.optJSONObject(index)?.toCandidate()?.let(::add)
      }
    }
    return selectAudibleCatalogCandidate(cleanTitle, authors, candidates)
  }

  private fun validateResponse(
    statusCode: Int,
    successful: Boolean,
    finalUrl: String,
    bodySize: Int,
  ) {
    if (!successful) throw IOException("Audible Catalog API の取得に失敗しました ($statusCode)")
    if (!isCatalogUrl(finalUrl)) throw IOException("Audible Catalog API 以外へリダイレクトされました")
    if (bodySize > MAX_RESPONSE_BYTES) throw IOException("Audible Catalog API の応答が大きすぎます")
  }

  private fun parseJson(body: ByteArray): JSONObject = runCatching {
    JSONObject(body.toString(Charsets.UTF_8))
  }.getOrElse { error ->
    throw IOException("Audible Catalog API の応答を解析できませんでした", error)
  }

  private fun headers(): Map<String, String> = mapOf(
    "Accept" to "application/json",
    "Accept-Language" to "ja-JP,ja;q=0.9",
  )
}

internal data class AudibleCatalogCandidate(
  val asin: String,
  val title: String,
  val authors: List<String>,
  val thumbnailUrl: String?,
)

internal fun selectAudibleCatalogCandidate(
  title: String,
  authors: List<String>,
  candidates: List<AudibleCatalogCandidate>,
): CoverLookupResult {
  val expectedTitle = normalizeBookText(title)
  if (expectedTitle.isEmpty()) return CoverLookupResult(CoverLookupStatus.NOT_FOUND)
  val expectedAuthors = authors.map(::normalizeBookText).filter(String::isNotEmpty).toSet()

  val matches = candidates.asSequence()
    .filter { it.thumbnailUrl != null }
    .filter { normalizeBookText(it.title) == expectedTitle }
    .filter { candidate ->
      expectedAuthors.isEmpty() || candidate.authors
        .map(::normalizeBookText)
        .filter(String::isNotEmpty)
        .any(expectedAuthors::contains)
    }
    .distinctBy { it.asin.uppercase(Locale.ROOT) }
    .toList()

  if (matches.isEmpty()) return CoverLookupResult(CoverLookupStatus.NOT_FOUND)
  if (matches.size != 1) return CoverLookupResult(CoverLookupStatus.AMBIGUOUS)
  val match = matches.single()
  return CoverLookupResult(
    status = CoverLookupStatus.FOUND,
    thumbnailUrl = match.thumbnailUrl,
    matchedIdentifier = "ASIN:${match.asin}",
  )
}

private fun JSONObject.toCandidate(): AudibleCatalogCandidate? {
  val asin = optString("asin").trim().takeIf(String::isNotEmpty) ?: return null
  val title = optString("title").trim().takeIf(String::isNotEmpty) ?: return null
  val authors = optJSONArray("authors")?.let { array ->
    buildList {
      for (index in 0 until array.length()) {
        array.optJSONObject(index)?.optString("name")?.trim()
          ?.takeIf(String::isNotEmpty)?.let(::add)
      }
    }
  }.orEmpty()
  return AudibleCatalogCandidate(
    asin = asin,
    title = title,
    authors = authors,
    thumbnailUrl = bestImage(optJSONObject("product_images")),
  )
}

private fun bestImage(images: JSONObject?): String? {
  if (images == null) return null
  return images.keys().asSequence().mapNotNull { key ->
    val size = key.toIntOrNull() ?: return@mapNotNull null
    val url = images.optString(key).trim().takeIf(String::isNotEmpty) ?: return@mapNotNull null
    if (!isTrustedImageUrl(url)) return@mapNotNull null
    size to url
  }.maxByOrNull { it.first }?.second
}

private fun isCatalogUrl(url: String): Boolean {
  val uri = runCatching { URI(url) }.getOrNull() ?: return false
  return uri.scheme.equals("https", ignoreCase = true) &&
    uri.host.equals("api.audible.co.jp", ignoreCase = true)
}

private fun isTrustedImageUrl(url: String): Boolean {
  val uri = runCatching { URI(url) }.getOrNull() ?: return false
  if (!uri.scheme.equals("https", ignoreCase = true)) return false
  val host = uri.host?.lowercase(Locale.ROOT) ?: return false
  return TRUSTED_IMAGE_HOST_SUFFIXES.any { host == it || host.endsWith(".$it") }
}

private fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.name())

private const val CATALOG_PRODUCTS_URL = "https://api.audible.co.jp/1.0/catalog/products"
private const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
private const val IMAGE_SIZE = 1215
private const val SEARCH_LIMIT = 10
private val AUDIBLE_ASIN = Regex("^[A-Z0-9]{10}$")
private val TRUSTED_IMAGE_HOST_SUFFIXES = setOf(
  "media-amazon.com",
  "ssl-images-amazon.com",
  "audible.co.jp",
)
