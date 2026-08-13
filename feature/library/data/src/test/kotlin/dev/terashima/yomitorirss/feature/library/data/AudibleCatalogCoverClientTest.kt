package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.core.network.HttpRequest
import dev.terashima.yomitorirss.core.network.HttpResponse
import java.util.ArrayDeque
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudibleCatalogCoverClientTest {
  @Test
  fun `ASIN で Catalog API の最大画像を取得する`() = runBlocking {
    val httpClient = QueueHttpClient(
      listOf(
        jsonResponse(
          finalUrl = "https://api.audible.co.jp/1.0/catalog/products/B012345678",
          body = """
            {
              "product": {
                "asin": "B012345678",
                "title": "テスト書籍",
                "authors": [{"name": "テスト著者"}],
                "product_images": {
                  "408": "https://m.media-amazon.com/images/I/small.jpg",
                  "1215": "https://m.media-amazon.com/images/I/large.jpg"
                }
              }
            }
          """.trimIndent(),
        ),
      ),
    )

    val result = AudibleCatalogCoverClient(httpClient).lookup(
      sourceId = "B012345678",
      title = "テスト書籍",
      authors = listOf("テスト著者"),
    )

    assertEquals(AudibleCoverProvider.CATALOG_API_ASIN, result.provider)
    assertEquals(CoverLookupStatus.FOUND, result.lookup.status)
    assertEquals("https://m.media-amazon.com/images/I/large.jpg", result.lookup.thumbnailUrl)
    assertEquals("ASIN:B012345678", result.lookup.matchedIdentifier)
    assertTrue(httpClient.requests.single().url.contains("/B012345678?"))
  }

  @Test
  fun `ASIN がなくても書名と著者が一致する商品を検索する`() = runBlocking {
    val httpClient = QueueHttpClient(
      listOf(
        jsonResponse(
          finalUrl = "https://api.audible.co.jp/1.0/catalog/products?title=test",
          body = """
            {
              "products": [
                {
                  "asin": "B000000001",
                  "title": "フォールバック書籍",
                  "authors": [{"name": "別の著者"}],
                  "product_images": {"1215": "https://m.media-amazon.com/images/I/wrong.jpg"}
                },
                {
                  "asin": "B000000002",
                  "title": "フォールバック書籍",
                  "authors": [{"name": "一致著者"}],
                  "product_images": {"1215": "https://m.media-amazon.com/images/I/match.jpg"}
                }
              ]
            }
          """.trimIndent(),
        ),
      ),
    )

    val result = AudibleCatalogCoverClient(httpClient).lookup(
      sourceId = "derived-source-id",
      title = "フォールバック書籍",
      authors = listOf("一致著者"),
    )

    assertEquals(AudibleCoverProvider.CATALOG_API_SEARCH, result.provider)
    assertEquals(CoverLookupStatus.FOUND, result.lookup.status)
    assertEquals("https://m.media-amazon.com/images/I/match.jpg", result.lookup.thumbnailUrl)
    assertEquals("ASIN:B000000002", result.lookup.matchedIdentifier)
    assertTrue(httpClient.requests.single().url.contains("title="))
    assertTrue(httpClient.requests.single().url.contains("author="))
  }

  @Test
  fun `同じ書名と著者の候補が複数なら曖昧として採用しない`() {
    val result = selectAudibleCatalogCandidate(
      title = "同名書籍",
      authors = listOf("同名著者"),
      candidates = listOf(
        AudibleCatalogCandidate(
          asin = "B000000003",
          title = "同名書籍",
          authors = listOf("同名著者"),
          thumbnailUrl = "https://m.media-amazon.com/images/I/one.jpg",
        ),
        AudibleCatalogCandidate(
          asin = "B000000004",
          title = "同名書籍",
          authors = listOf("同名著者"),
          thumbnailUrl = "https://m.media-amazon.com/images/I/two.jpg",
        ),
      ),
    )

    assertEquals(CoverLookupStatus.AMBIGUOUS, result.status)
  }

  @Test
  fun `信頼していない画像ホストは採用しない`() = runBlocking {
    val httpClient = QueueHttpClient(
      listOf(
        jsonResponse(
          finalUrl = "https://api.audible.co.jp/1.0/catalog/products/B012345678",
          body = """
            {
              "product": {
                "asin": "B012345678",
                "title": "テスト書籍",
                "authors": [],
                "product_images": {"1215": "https://example.com/cover.jpg"}
              }
            }
          """.trimIndent(),
        ),
      ),
    )

    val result = AudibleCatalogCoverClient(httpClient).lookup(
      sourceId = "B012345678",
      title = "",
      authors = emptyList(),
    )

    assertEquals(AudibleCoverProvider.CATALOG_API_ASIN, result.provider)
    assertEquals(CoverLookupStatus.NOT_FOUND, result.lookup.status)
  }
}

private class QueueHttpClient(responses: List<HttpResponse>) : HttpClient {
  private val responses = ArrayDeque(responses)
  val requests = mutableListOf<HttpRequest>()

  override suspend fun execute(request: HttpRequest): HttpResponse {
    requests += request
    return responses.removeFirst()
  }
}

private fun jsonResponse(
  finalUrl: String,
  body: String,
  statusCode: Int = 200,
): HttpResponse = HttpResponse(
  statusCode = statusCode,
  reasonPhrase = "OK",
  finalUrl = finalUrl,
  headers = emptyMap(),
  body = body.toByteArray(),
)
