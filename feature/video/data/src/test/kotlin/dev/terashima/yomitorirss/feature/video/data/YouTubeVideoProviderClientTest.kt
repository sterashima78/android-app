package dev.terashima.yomitorirss.feature.video.data

import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.core.network.HttpRequest
import dev.terashima.yomitorirss.core.network.HttpResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeVideoProviderClientTest {
  @Test
  fun `購読は通常動画フィードを取得してVideo provider feedへ変換する`() = runBlocking {
    val http = RecordingHttpClient(feedResponse())
    val client = YouTubeVideoProviderClient(http)

    val feed = client.subscribe("https://www.youtube.com/channel/$CHANNEL_ID")

    assertEquals(
      "https://www.youtube.com/feeds/videos.xml?playlist_id=UULFabcdefghijklmnopqrstuv",
      http.requests.single().url,
    )
    assertEquals(4L * 1024 * 1024, http.requests.single().maxResponseBytes)
    assertEquals(CHANNEL_ID, feed.sourceId)
    assertEquals("Test Channel", feed.title)
    assertEquals("https://www.youtube.com/channel/$CHANNEL_ID", feed.sourceUrl)
    val video = feed.videos.single()
    assertEquals("video-1", video.id)
    assertEquals("Regular video", video.title)
    assertEquals("https://www.youtube.com/watch?v=video-1", video.url)
    assertEquals("https://i.ytimg.com/vi/video-1/hqdefault.jpg", video.thumbnailUrl)
    assertEquals(1_767_225_600_000L, video.publishedAtEpochMillis)
  }

  @Test
  fun `購読URLはchannel ID形式のHTTPS URLだけを受け付ける`() = runBlocking {
    val http = RecordingHttpClient(feedResponse())
    val client = YouTubeVideoProviderClient(http)

    val error = runCatching {
      client.subscribe("https://example.invalid/channel/$CHANNEL_ID")
    }.exceptionOrNull()

    assertTrue(error is IllegalArgumentException)
    assertTrue(http.requests.isEmpty())
  }

  @Test
  fun `取得結果のchannel IDが要求と異なる場合は購読しない`() = runBlocking {
    val http = RecordingHttpClient(feedResponse(channelId = OTHER_CHANNEL_ID))
    val client = YouTubeVideoProviderClient(http)

    val error = runCatching {
      client.subscribe("https://www.youtube.com/channel/$CHANNEL_ID")
    }.exceptionOrNull()

    assertTrue(error is IllegalArgumentException)
  }

  private fun feedResponse(channelId: String = CHANNEL_ID): HttpResponse {
    val xml = """
      <?xml version="1.0" encoding="UTF-8"?>
      <feed xmlns="http://www.w3.org/2005/Atom" xmlns:yt="http://www.youtube.com/xml/schemas/2015">
        <yt:channelId>$channelId</yt:channelId>
        <title>Test Channel</title>
        <entry>
          <yt:videoId>video-1</yt:videoId>
          <yt:channelId>$channelId</yt:channelId>
          <title>Regular video</title>
          <link rel="alternate" href="https://www.youtube.com/watch?v=video-1" />
          <published>2026-01-01T00:00:00Z</published>
        </entry>
      </feed>
    """.trimIndent()
    return HttpResponse(
      statusCode = 200,
      reasonPhrase = "OK",
      finalUrl = "https://example.invalid/feed",
      headers = emptyMap(),
      body = xml.toByteArray(),
    )
  }

  private class RecordingHttpClient(
    private val response: HttpResponse,
  ) : HttpClient {
    val requests = mutableListOf<HttpRequest>()

    override suspend fun execute(request: HttpRequest): HttpResponse {
      requests += request
      return response
    }
  }

  private companion object {
    const val CHANNEL_ID = "UCabcdefghijklmnopqrstuv"
    const val OTHER_CHANNEL_ID = "UCbcdefghijklmnopqrstuvw"
  }
}
