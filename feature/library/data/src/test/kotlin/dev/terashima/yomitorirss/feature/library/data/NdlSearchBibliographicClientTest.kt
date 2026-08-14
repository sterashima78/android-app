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

class NdlSearchBibliographicClientTest {
  @Test
  fun `日本語タイトルからISBNを解決する`() = runBlocking {
    val isbn = "9781234567897"
    val httpClient = RecordingHttpClient(
      """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0">
          <channel>
            <item>
              <title>合成テスト書籍 / テスト著者</title>
              <description>テスト著者. 合成テスト書籍, テスト出版社, 2026. ISBN $isbn</description>
            </item>
          </channel>
        </rss>
      """.trimIndent(),
    )

    val result = NdlSearchBibliographicClient(httpClient).lookupByTitle(book())

    assertEquals(CoverLookupStatus.NOT_FOUND, result.lookup.status)
    assertEquals("ISBN_RESOLVED", result.step.reason)
    assertEquals(isbn, result.resolvedIdentifiers.single().value)
    assertEquals(BookIdentifierRelation.SAME_WORK, result.resolvedIdentifiers.single().relation)
    assertTrue(httpClient.lastRequest?.url.orEmpty().contains("mediatype=books"))
    assertTrue(httpClient.lastRequest?.url.orEmpty().contains("creator="))
  }

  @Test
  fun `複数ISBN候補は曖昧として採用しない`() = runBlocking {
    val httpClient = RecordingHttpClient(
      """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0">
          <channel>
            <item>
              <title>合成テスト書籍 / テスト著者</title>
              <description>ISBN 9781234567897</description>
            </item>
            <item>
              <title>合成テスト書籍 / テスト著者</title>
              <description>ISBN 9780306406157</description>
            </item>
          </channel>
        </rss>
      """.trimIndent(),
    )

    val result = NdlSearchBibliographicClient(httpClient).lookupByTitle(book())

    assertEquals(CoverLookupStatus.AMBIGUOUS, result.lookup.status)
    assertEquals("MULTIPLE_ISBN_MATCHES", result.step.reason)
    assertTrue(result.resolvedIdentifiers.isEmpty())
  }

  @Test
  fun `検索タイトルを接頭辞に含む別タイトルはISBNを採用しない`() = runBlocking {
    val httpClient = RecordingHttpClient(
      """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0">
          <channel>
            <item>
              <title>合成テスト書籍 完全版 / テスト著者</title>
              <description>ISBN 9781234567897</description>
            </item>
          </channel>
        </rss>
      """.trimIndent(),
    )

    val result = NdlSearchBibliographicClient(httpClient).lookupByTitle(book())

    assertEquals(CoverLookupStatus.NOT_FOUND, result.lookup.status)
    assertEquals("TITLE_MISMATCH", result.step.reason)
    assertTrue(result.resolvedIdentifiers.isEmpty())
  }

  @Test
  fun `日本語文字を含むタイトルだけNDL候補とする`() {
    assertTrue(isLikelyJapaneseBookTitle("子どもの感情コントロール"))
    assertEquals(false, isLikelyJapaneseBookTitle("Synthetic Book"))
  }

  private fun book() = LibraryBook(
    source = LibrarySource.KINDLE,
    sourceId = "B0TEST0001",
    title = "合成テスト書籍 (Japanese Edition)",
    authors = listOf("テスト著者"),
    publisher = null,
    publishedDate = null,
    description = null,
    isbn10 = null,
    isbn13 = null,
    thumbnailUrl = null,
    infoUrl = null,
  )

  private class RecordingHttpClient(private val body: String) : HttpClient {
    var lastRequest: HttpRequest? = null

    override suspend fun execute(request: HttpRequest): HttpResponse {
      lastRequest = request
      return HttpResponse(
        statusCode = 200,
        reasonPhrase = "OK",
        finalUrl = request.url,
        headers = mapOf("Content-Type" to listOf("application/rss+xml")),
        body = body.toByteArray(),
      )
    }
  }
}
