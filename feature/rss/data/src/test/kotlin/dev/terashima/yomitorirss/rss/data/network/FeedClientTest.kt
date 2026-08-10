package dev.terashima.yomitorirss.feature.rss.data

import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.core.network.HttpRequest
import dev.terashima.yomitorirss.core.network.HttpResponse
import dev.terashima.yomitorirss.feature.rss.data.network.FeedClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedClientTest {
  private val client = FeedClient()

  @Test
  fun `HTTP URL は HTTPS に更新する`() {
    assertEquals(
      "https://b.hatena.ne.jp/hotentry.rss?mode=general",
      client.normalizeInputUrl("http://b.hatena.ne.jp/hotentry.rss?mode=general"),
    )
  }

  @Test
  fun `RSS 1_0 の名前空間付き日付を解析する`() {
    val feed = client.parseFeed(
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#" xmlns="http://purl.org/rss/1.0/" xmlns:dc="http://purl.org/dc/elements/1.1/">
        <channel rdf:about="https://example.com/rss"><title>技術ニュース</title><link>https://example.com/</link></channel>
        <item rdf:about="https://example.com/a"><title>記事A</title><link>https://example.com/a</link><dc:date>2026-08-06T08:00:00Z</dc:date></item>
      </rdf:RDF>
      """.trimIndent(),
      "https://example.com/rss",
    )
    assertEquals("技術ニュース", feed.title)
    assertEquals(1, feed.articles.size)
    assertEquals("2026-08-06T08:00:00Z", feed.articles.single().publishedAt)
  }

  @Test
  fun `Atom の相対URLを絶対URLにする`() {
    val feed = client.parseFeed(
      """
      <feed xmlns="http://www.w3.org/2005/Atom"><title>Example</title><link rel="alternate" href="/" /><entry><id>tag:example.com,2026:a</id><title>Article</title><link href="/articles/a" /><updated>2026-08-06T09:00:00+09:00</updated></entry></feed>
      """.trimIndent(),
      "https://example.com/feed.xml",
    )
    assertEquals("https://example.com/articles/a", feed.articles.single().url)
    assertTrue(feed.articles.single().identityKey.isNotBlank())
  }

  @Test
  fun `更新時に ETag と Last-Modified を条件付きリクエストへ渡す`() = runBlocking {
    val httpClient = RecordingHttpClient(
      HttpResponse(
        statusCode = 304,
        reasonPhrase = "Not Modified",
        finalUrl = "https://example.com/feed.xml",
        headers = emptyMap(),
        body = byteArrayOf(),
      ),
    )
    val feedClient = FeedClient(httpClient)

    val result = feedClient.fetchFeed(
      url = "https://example.com/feed.xml",
      etag = "\"v1\"",
      lastModified = "Sat, 08 Aug 2026 05:00:00 GMT",
    )

    assertTrue(result.notModified)
    assertEquals("\"v1\"", httpClient.lastRequest?.headers?.get("If-None-Match"))
    assertEquals(
      "Sat, 08 Aug 2026 05:00:00 GMT",
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
