package dev.terashima.yomitorirss.feature.article.data

import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.core.network.HttpRequest
import dev.terashima.yomitorirss.core.network.HttpResponse
import dev.terashima.yomitorirss.feature.article.data.network.ArticleContentClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ArticleContentClientTest {
  @Test
  fun `空のURLは通信前に拒否する`() {
    val error = assertThrows(IllegalArgumentException::class.java) {
      runBlocking { ArticleContentClient().fetchArticleText("   ") }
    }

    assertEquals("URLを入力してください", error.message)
  }

  @Test
  fun `記事本文抽出は article data 内で行う`() = runBlocking {
    val httpClient = RecordingHttpClient(
      HttpResponse(
        statusCode = 200,
        reasonPhrase = "OK",
        finalUrl = "https://example.com/articles/1",
        headers = mapOf("Content-Type" to listOf("text/html; charset=UTF-8")),
        body = """
          <html><body>
            <header>header</header>
            <article><h1>Title</h1><p>Article body.</p></article>
            <footer>footer</footer>
          </body></html>
        """.trimIndent().toByteArray(),
      ),
    )

    val text = ArticleContentClient(httpClient).fetchArticleText("https://example.com/articles/1")

    assertEquals("Title Article body.", text)
    assertEquals(
      "text/html, application/xhtml+xml;q=0.9, */*;q=0.5",
      httpClient.lastRequest?.headers?.get("Accept"),
    )
  }
}

private class RecordingHttpClient(
  private val response: HttpResponse,
) : HttpClient {
  var lastRequest: HttpRequest? = null
    private set

  override suspend fun execute(request: HttpRequest): HttpResponse {
    lastRequest = request
    return response
  }
}
