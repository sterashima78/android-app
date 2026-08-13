package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.core.network.HttpRequest
import dev.terashima.yomitorirss.core.network.HttpResponse
import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibrarySource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenLibraryCoverClientTest {
  @Test
  fun `タイトル 著者 巻数が一致する候補だけを採用する`() {
    val result = selectTitleCandidate(
      book = book(title = "星間航路 第3巻", authors = listOf("山田 太郎")),
      candidates = listOf(
        OpenLibraryCandidate(
          key = "/works/OLTEST3W",
          title = "星間航路 第３巻",
          authors = listOf("山田太郎"),
          isbns = emptyList(),
          coverId = 42,
        ),
        OpenLibraryCandidate(
          key = "/works/OLTEST4W",
          title = "星間航路 第4巻",
          authors = listOf("山田太郎"),
          isbns = emptyList(),
          coverId = 43,
        ),
      ),
    )

    assertEquals(CoverLookupStatus.FOUND, result.status)
    assertEquals("https://covers.openlibrary.org/b/id/42-L.jpg", result.thumbnailUrl)
    assertEquals("/works/OLTEST3W", result.matchedIdentifier)
  }

  @Test
  fun `同一タイトルに複数の表紙候補が残る場合は曖昧とする`() {
    val result = selectTitleCandidate(
      book = book(title = "架空書籍", authors = emptyList()),
      candidates = listOf(
        OpenLibraryCandidate(
          key = "/works/OLTEST1W",
          title = "架空書籍",
          authors = emptyList(),
          isbns = emptyList(),
          coverId = 10,
        ),
        OpenLibraryCandidate(
          key = "/works/OLTEST2W",
          title = "架空書籍",
          authors = emptyList(),
          isbns = emptyList(),
          coverId = 11,
        ),
      ),
    )

    assertEquals(CoverLookupStatus.AMBIGUOUS, result.status)
    assertEquals(null, result.thumbnailUrl)
  }

  @Test
  fun `著者が一致しない候補は採用しない`() {
    val result = selectTitleCandidate(
      book = book(title = "架空書籍", authors = listOf("著者A")),
      candidates = listOf(
        OpenLibraryCandidate(
          key = "/works/OLTEST1W",
          title = "架空書籍",
          authors = listOf("著者B"),
          isbns = emptyList(),
          coverId = 10,
        ),
      ),
    )

    assertEquals(CoverLookupStatus.NOT_FOUND, result.status)
  }

  @Test
  fun `ISBNがある場合は完全一致した表紙を使う`() = runBlocking {
    val isbn = "9781234567897"
    val httpClient = RecordingHttpClient(
      body = """
        {
          "docs": [
            {
              "key": "/works/OLTESTW",
              "title": "Synthetic Book",
              "author_name": ["Example Author"],
              "isbn": ["$isbn"],
              "cover_i": 99
            }
          ]
        }
      """.trimIndent(),
    )

    val result = OpenLibraryCoverClient(httpClient).lookup(
      book(
        title = "Synthetic Book",
        authors = listOf("Example Author"),
        isbn13 = isbn,
      ),
    )

    assertEquals(CoverLookupStatus.FOUND, result.status)
    assertEquals(
      "https://covers.openlibrary.org/b/id/99-L.jpg",
      result.thumbnailUrl,
    )
    assertEquals("ISBN:$isbn", result.matchedIdentifier)
    assertTrue(httpClient.lastRequest?.url.orEmpty().contains("isbn%3A$isbn"))
  }

  @Test
  fun `巻数表現を抽出できる`() {
    assertEquals(12, explicitVolumeNumber("架空書籍 第12巻"))
    assertEquals(7, explicitVolumeNumber("Synthetic Book Vol. 7"))
    assertEquals(3, explicitVolumeNumber("Synthetic Book #3"))
    assertEquals(null, explicitVolumeNumber("Synthetic Book"))
  }

  @Test
  fun `Amazon OGPから信頼可能な表紙URLを取得する`() {
    val html = """
      <meta property="og:image" content="https://m.media-amazon.com/images/I/synthetic-cover.jpg">
    """.trimIndent()

    assertEquals(
      "https://m.media-amazon.com/images/I/synthetic-cover.jpg",
      extractAmazonOgCoverUrl(html),
    )
  }

  @Test
  fun `Amazon商品画像では高解像度属性を優先する`() {
    val html = """
      <img id="ebooksImgBlkFront"
        src="https://m.media-amazon.com/images/I/low.jpg"
        data-old-hires="https://m.media-amazon.com/images/I/high.jpg">
    """.trimIndent()

    assertEquals(
      "https://m.media-amazon.com/images/I/high.jpg",
      extractAmazonProductImageUrl(html),
    )
  }

  @Test
  fun `Amazon商品URLは要求ASINとホストを検証する`() {
    assertTrue(
      isAmazonProductPageForAsin(
        "https://www.amazon.co.jp/Synthetic-Book/dp/B0TEST0001/ref=test",
        "B0TEST0001",
      ),
    )
    assertEquals(
      false,
      isAmazonProductPageForAsin(
        "https://www.amazon.co.jp/errors/validateCaptcha",
        "B0TEST0001",
      ),
    )
    assertEquals(
      false,
      isAmazonProductPageForAsin(
        "https://example.com/dp/B0TEST0001",
        "B0TEST0001",
      ),
    )
  }

  @Test
  fun `信頼していないAmazon画像ホストは採用しない`() {
    val html = """
      <meta property="og:image" content="https://example.com/cover.jpg">
      <img id="landingImage" src="https://example.com/cover.jpg">
    """.trimIndent()

    assertEquals(null, extractAmazonOgCoverUrl(html))
    assertEquals(null, extractAmazonProductImageUrl(html))
  }

  private fun book(
    title: String,
    authors: List<String>,
    isbn10: String? = null,
    isbn13: String? = null,
  ) = LibraryBook(
    source = LibrarySource.KINDLE,
    sourceId = "TESTBOOK01",
    title = title,
    authors = authors,
    publisher = null,
    publishedDate = null,
    description = null,
    isbn10 = isbn10,
    isbn13 = isbn13,
    thumbnailUrl = null,
    infoUrl = null,
  )

  private class RecordingHttpClient(
    private val body: String,
  ) : HttpClient {
    var lastRequest: HttpRequest? = null

    override suspend fun execute(request: HttpRequest): HttpResponse {
      lastRequest = request
      return HttpResponse(
        statusCode = 200,
        reasonPhrase = "OK",
        finalUrl = request.url,
        headers = emptyMap(),
        body = body.toByteArray(),
      )
    }
  }
}
