package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.core.network.HttpRequest
import dev.terashima.yomitorirss.core.network.HttpResponse
import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibrarySource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleBooksCoverClientTest {
  @Test
  fun `Japanese Editionを検索タイトルから除外して表紙を取得する`() = runBlocking {
    val httpClient = RecordingHttpClient(
      body = """
        {
          "items": [
            {
              "id": "synthetic-volume",
              "volumeInfo": {
                "title": "Synthetic Parenting Book",
                "authors": ["Example Author"],
                "imageLinks": {
                  "thumbnail": "http://books.google.com/synthetic-cover.jpg"
                }
              }
            }
          ]
        }
      """.trimIndent(),
    )

    val result = GoogleBooksCoverClient(httpClient).lookup(
      book(
        title = "Synthetic Parenting Book (Japanese Edition)",
        authors = listOf("Example Author"),
      ),
    )

    assertEquals(CoverLookupStatus.FOUND, result.lookup.status)
    assertEquals("https://books.google.com/synthetic-cover.jpg", result.lookup.thumbnailUrl)
    assertEquals("TITLE_AUTHOR_MATCH", result.step.reason)
    assertFalse(httpClient.lastRequest?.url.orEmpty().contains("Japanese+Edition"))
  }

  @Test
  fun `著者不一致を診断理由として残す`() = runBlocking {
    val httpClient = RecordingHttpClient(
      body = """
        {
          "items": [
            {
              "id": "synthetic-volume",
              "volumeInfo": {
                "title": "Synthetic Book",
                "authors": ["Different Author"],
                "imageLinks": {"thumbnail": "https://books.google.com/cover.jpg"}
              }
            }
          ]
        }
      """.trimIndent(),
    )

    val result = GoogleBooksCoverClient(httpClient).lookup(
      book(title = "Synthetic Book", authors = listOf("Expected Author")),
    )

    assertEquals(CoverLookupStatus.NOT_FOUND, result.lookup.status)
    assertEquals("AUTHOR_MISMATCH", result.step.reason)
    assertEquals(1, result.step.candidateCount)
    assertEquals(1, result.step.titleMatchCount)
    assertEquals(0, result.step.authorMatchCount)
  }

  @Test
  fun `ISBNがある場合はISBN検索を優先する`() = runBlocking {
    val isbn = "9781234567897"
    val httpClient = RecordingHttpClient(
      body = """
        {
          "items": [
            {
              "id": "synthetic-volume",
              "volumeInfo": {
                "title": "Synthetic Book",
                "industryIdentifiers": [
                  {"type": "ISBN_13", "identifier": "$isbn"}
                ],
                "imageLinks": {"thumbnail": "https://books.google.com/isbn-cover.jpg"}
              }
            }
          ]
        }
      """.trimIndent(),
    )

    val result = GoogleBooksCoverClient(httpClient).lookup(
      book(title = "Synthetic Book", authors = emptyList(), isbn13 = isbn),
    )

    assertEquals(CoverLookupStatus.FOUND, result.lookup.status)
    assertEquals("ISBN_MATCH", result.step.reason)
    assertEquals("ISBN", result.step.attributes["searchMode"])
    assertTrue(httpClient.lastRequest?.url.orEmpty().contains("isbn%3A$isbn"))
  }

  private fun book(
    title: String,
    authors: List<String>,
    isbn13: String? = null,
  ) = LibraryBook(
    source = LibrarySource.KINDLE,
    sourceId = "B0TEST0001",
    title = title,
    authors = authors,
    publisher = null,
    publishedDate = null,
    description = null,
    isbn10 = null,
    isbn13 = isbn13,
    thumbnailUrl = null,
    infoUrl = null,
  )

  private class RecordingHttpClient(
    private val body: String,
    private val statusCode: Int = 200,
  ) : HttpClient {
    var lastRequest: HttpRequest? = null

    override suspend fun execute(request: HttpRequest): HttpResponse {
      lastRequest = request
      return HttpResponse(
        statusCode = statusCode,
        reasonPhrase = if (statusCode in 200..299) "OK" else "Error",
        finalUrl = request.url,
        headers = mapOf("Content-Type" to listOf("application/json")),
        body = body.toByteArray(),
      )
    }
  }
}
