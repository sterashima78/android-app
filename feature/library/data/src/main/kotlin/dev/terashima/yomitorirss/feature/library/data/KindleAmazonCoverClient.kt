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
    if (isAmazonChallengePage(html)) {
      throw IOException("Amazon 商品ページでアクセス確認が要求されました")
    }
    extractAmazonOgCoverUrl(html)?.let { imageUrl ->
      return found(asin, imageUrl, KindleCoverProvider.AMAZON_PRODUCT_PAGE_OGP)
    }
    extractAmazonProductImageUrl(html)?.let { imageUrl ->
      return found(asin, imageUrl, KindleCoverProvider.AMAZON_PRODUCT_PAGE_IMAGE)
    }
    return notFound(asin)
  }
