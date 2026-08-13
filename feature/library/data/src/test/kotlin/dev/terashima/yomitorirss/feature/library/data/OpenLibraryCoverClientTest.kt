package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.core.network.HttpRequest
import dev.terashima.yomitorirss.core.network.HttpResponse
import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibrarySource
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
  fun `同じクライアントで複数の検索を実行できる`() = runBlocking {
    val httpClient = RecordingHttpClient(body = "{\"docs\":[]}")
    val client = OpenLibraryCoverClient(httpClient)

    client.lookup(book(title = "Synthetic Book A", authors = emptyList()))
    client.lookup(book(title = "Synthetic Book B", authors = emptyList()))

    assertEquals(2, httpClient.requestCount)
  }

  @Test
  fun `429とサーバーエラーは一時エラーとして再試行対象にする`() = runBlocking {
    val httpClient = RecordingHttpClient(body = "{}", statusCode = 429)
    val error = runCatching {
      OpenLibraryCoverClient(httpClient).lookup(
        book(title = "Synthetic Book", authors = emptyList()),
      )
    }.exceptionOrNull()

    assertTrue(error is IOException)
    assertTrue(isRetryableOpenLibraryStatus(408))
    assertTrue(isRetryableOpenLibraryStatus(429))
    assertTrue(isRetryableOpenLibraryStatus(503))
    assertFalse(isRetryableOpenLibraryStatus(404))
  }

  @Test
  fun `恒久的なHTTPエラーは取得エラーとして確定する`() = runBlocking {
    val result = OpenLibraryCoverClient(
      RecordingHttpClient(body = "{}", statusCode = 404),
    ).lookup(book(title = "Synthetic Book", authors = emptyList()))

    assertEquals(CoverLookupStatus.ERROR, result.status)
  }

  @Test
  fun `巻数表現を抽出できる`() {
    assertEquals(12, explicitVolumeNumber("架空書籍 第12巻"))
    assertEquals(7, explicitVolumeNumber("Synthetic Book Vol. 7"))
    assertEquals(3, explicitVolumeNumber("Synthetic Book #3"))
    assertEquals(null, explicitVolumeNumber("Synthetic Book"))
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
    private val statusCode: Int = 200,
  ) : HttpClient {
    var lastRequest: HttpRequest? = null
    var requestCount: Int = 0

    override suspend fun execute(request: HttpRequest): HttpResponse {
      lastRequest = request
      requestCount++
      return HttpResponse(
        statusCode = statusCode,
        reasonPhrase = if (statusCode in 200..299) "OK" else "Error",
        finalUrl = request.url,
        headers = emptyMap(),
        body = body.toByteArray(),
      )
    }
  }
}
