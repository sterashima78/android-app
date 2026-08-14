package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.core.network.HttpRequest
import dev.terashima.yomitorirss.core.network.HttpResponse
import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibrarySource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleBooksRetryTest {
  @Test
  fun `503はその場で1回だけ再試行して成功結果を使う`() = runBlocking {
    val client = SequencedHttpClient(
      listOf(
        ResponseSpec(503, "{}"),
        ResponseSpec(
          200,
          """
            {
              "items": [
                {
                  "id": "synthetic-volume",
                  "volumeInfo": {
                    "title": "Synthetic Book",
                    "authors": ["Example Author"],
                    "imageLinks": {"thumbnail": "https://books.google.com/cover.jpg"}
                  }
                }
              ]
            }
          """.trimIndent(),
        ),
      ),
    )

    val result = GoogleBooksCoverClient(client).lookupByTitle(
      book(),
      accessToken = "synthetic-access-token",
    )

    assertEquals(CoverLookupStatus.FOUND, result.lookup.status)
    assertEquals(2, client.requestCount)
    assertEquals("2", result.step.attributes["requestAttempts"])
  }

  @Test
  fun `429は即時再試行しない`() = runBlocking {
    val client = SequencedHttpClient(listOf(ResponseSpec(429, "{}")))

    val error = runCatching {
      GoogleBooksCoverClient(client).lookupByTitle(
        book(),
        accessToken = "synthetic-access-token",
      )
    }.exceptionOrNull() as CoverProviderIOException

    assertEquals(1, client.requestCount)
    assertEquals("HTTP_RETRYABLE", error.step.reason)
    assertEquals("1", error.step.attributes["requestAttempts"])
  }

  private fun book() = LibraryBook(
    source = LibrarySource.KINDLE,
    sourceId = "B0TEST0001",
    title = "Synthetic Book",
    authors = listOf("Example Author"),
    publisher = null,
    publishedDate = null,
    description = null,
    isbn10 = null,
    isbn13 = null,
    thumbnailUrl = null,
    infoUrl = null,
  )

  private data class ResponseSpec(val statusCode: Int, val body: String)

  private class SequencedHttpClient(private val responses: List<ResponseSpec>) : HttpClient {
    var requestCount = 0

    override suspend fun execute(request: HttpRequest): HttpResponse {
      val spec = responses[requestCount.coerceAtMost(responses.lastIndex)]
      requestCount++
      return HttpResponse(
        statusCode = spec.statusCode,
        reasonPhrase = if (spec.statusCode in 200..299) "OK" else "Error",
        finalUrl = request.url,
        headers = mapOf("Content-Type" to listOf("application/json")),
        body = spec.body.toByteArray(),
      )
    }
  }
}
