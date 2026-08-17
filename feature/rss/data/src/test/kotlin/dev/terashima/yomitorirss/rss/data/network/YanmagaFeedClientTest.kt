package dev.terashima.yomitorirss.feature.rss.data.network

import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.core.network.HttpRequest
import dev.terashima.yomitorirss.core.network.HttpResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YanmagaFeedClientTest {
  @Test
  fun `ヤンマガWeb作品URLだけを対象にして正規URLへ統一する`() {
    val client = YanmagaFeedClient()

    assertTrue(client.supports("https://yanmaga.jp/comics/sample_work"))
    assertTrue(client.supports("https://www.yanmaga.jp/comics/sample_work/?sort=older"))
    assertFalse(client.supports("https://yanmaga.jp/comics/sample_work/episode-1"))
    assertFalse(client.supports("https://yanmaga.jp/comics/series"))
    assertFalse(client.supports("https://example.com/comics/sample_work"))
    assertEquals(
      "https://yanmaga.jp/comics/sample_work",
      client.canonicalWorkUrl("https://www.yanmaga.jp/comics/sample_work/?sort=older#episodes"),
    )
  }

  @Test
  fun `無料公開中のエピソードだけをフィードとして解析する`() = runBlocking {
    val pageUrl = "https://yanmaga.jp/comics/sample_work"
    val httpClient = RecordingHttpClient(
      HttpResponse(
        statusCode = 200,
        reasonPhrase = "OK",
        finalUrl = pageUrl,
        headers = mapOf("Content-Type" to listOf("text/html; charset=UTF-8")),
        body = """
          <!doctype html>
          <html lang="ja">
            <head><title>サンプル作品 | ヤンマガWeb</title></head>
            <body>
              <main>
                <h1>サンプル作品</h1>
                <div class="mod-episode-list">
                  <div class="mod-episode-item" data-is-free="true">
                    <a class="mod-episode-link" href="/comics/sample_work/free-episode">
                      <span class="mod-episode-title">第1話 サンプル</span>
                    </a>
                    <span class="mod-episode-date">2026/08/17</span>
                  </div>
                  <div class="mod-episode-item" data-is-free="false">
                    <a class="mod-episode-link" href="/comics/sample_work/paid-episode">
                      <span class="mod-episode-title">第2話 サンプル</span>
                    </a>
                    <span class="mod-episode-date">2026/08/24</span>
                  </div>
                </div>
              </main>
            </body>
          </html>
        """.trimIndent().toByteArray(),
      ),
    )
    val client = YanmagaFeedClient(httpClient)

    val result = client.fetchFeed("$pageUrl?sort=older")
    val feed = requireNotNull(result.feed)

    assertEquals(pageUrl, httpClient.lastRequest?.url)
    assertEquals("サンプル作品", feed.title)
    assertEquals(pageUrl, feed.feedUrl)
    assertEquals(pageUrl, feed.siteUrl)
    assertEquals(1, feed.articles.size)
    assertEquals("第1話 サンプル", feed.articles.single().title)
    assertEquals("https://yanmaga.jp/comics/sample_work/free-episode", feed.articles.single().url)
    assertEquals("2026-08-16T15:00:00Z", feed.articles.single().publishedAt)
  }

  @Test
  fun `更新時に条件付きリクエストを引き継ぐ`() = runBlocking {
    val httpClient = RecordingHttpClient(
      HttpResponse(
        statusCode = 304,
        reasonPhrase = "Not Modified",
        finalUrl = "https://yanmaga.jp/comics/sample_work",
        headers = emptyMap(),
        body = byteArrayOf(),
      ),
    )
    val client = YanmagaFeedClient(httpClient)

    val result = client.fetchFeed(
      url = "https://yanmaga.jp/comics/sample_work",
      etag = "\"sample-v1\"",
      lastModified = "Mon, 17 Aug 2026 00:00:00 GMT",
    )

    assertTrue(result.notModified)
    assertEquals("\"sample-v1\"", httpClient.lastRequest?.headers?.get("If-None-Match"))
    assertEquals(
      "Mon, 17 Aug 2026 00:00:00 GMT",
      httpClient.lastRequest?.headers?.get("If-Modified-Since"),
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
