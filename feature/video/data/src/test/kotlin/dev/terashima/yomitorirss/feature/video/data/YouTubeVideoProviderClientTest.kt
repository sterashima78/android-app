package dev.terashima.yomitorirss.feature.video.data

import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.core.network.HttpRequest
import dev.terashima.yomitorirss.core.network.HttpResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
  fun `プレイリストのVideosではなく配信元のauthor名を購読タイトルとする`() = runBlocking {
    val http = RecordingHttpClient(feedResponseWithPlaylistTitle())
    val client = YouTubeVideoProviderClient(http)

    val feed = client.subscribe("https://www.youtube.com/channel/$CHANNEL_ID")

    assertEquals("Public Channel", feed.title)
    assertEquals("https://www.youtube.com/feeds/videos.xml?playlist_id=UULFabcdefghijklmnopqrstuv", http.requests.single().url)
    assertEquals("video-1", feed.videos.single().id)
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

  @Test
  fun `チャンネル名ヒントはチャンネルURLと2形式のフィードURLに対応する`() = runBlocking {
    val http = RecordingHttpClient(feedResponse())
    val client = YouTubeVideoProviderClient(http)
    val urls = listOf(
      "https://www.youtube.com/channel/$CHANNEL_ID",
      "https://www.youtube.com/channel/$CHANNEL_ID/videos",
      "https://www.youtube.com/feeds/videos.xml?channel_id=$CHANNEL_ID",
      "https://www.youtube.com/feeds/videos.xml?playlist_id=UULFabcdefghijklmnopqrstuv",
    )

    urls.forEach { sourceUrl ->
      assertEquals("Test Channel", client.channelNameHint(sourceUrl))
    }
    assertEquals(4, http.requests.size)
    assertTrue(http.requests.all { it.url == "https://www.youtube.com/feeds/videos.xml?channel_id=$CHANNEL_ID" })
  }

  @Test
  fun `不正なチャンネルURLからは名前を取得せず通信もしない`() = runBlocking {
    val http = RecordingHttpClient(feedResponse())
    val client = YouTubeVideoProviderClient(http)

    listOf(
      "http://www.youtube.com/channel/$CHANNEL_ID",
      "https://www.youtube.com.evil.invalid/channel/$CHANNEL_ID",
      "https://www.youtube.com/channel/$CHANNEL_ID?extra=true",
      "https://www.youtube.com/feeds/videos.xml?channel_id=$CHANNEL_ID&extra=true",
      "https://www.youtube.com/feeds/videos.xml?playlist_id=UULFbad",
    ).forEach { assertNull(client.channelNameHint(it)) }
    assertTrue(http.requests.isEmpty())
  }

  @Test
  fun `チャンネル名ヒントは取得したチャンネルIDが異なる場合には採用しない`() = runBlocking {
    val http = RecordingHttpClient(feedResponse(channelId = OTHER_CHANNEL_ID))
    val client = YouTubeVideoProviderClient(http)

    assertNull(client.channelNameHint("https://www.youtube.com/channel/$CHANNEL_ID"))
  }

  private fun feedResponseWithPlaylistTitle(): HttpResponse {
    val xml = """
      <?xml version="1.0" encoding="UTF-8"?>
      <feed xmlns="http://www.w3.org/2005/Atom" xmlns:yt="http://www.youtube.com/xml/schemas/2015">
        <yt:channelId>$CHANNEL_ID</yt:channelId>
        <title>Videos</title>
        <author><name>Public Channel</name><uri>https://www.youtube.com/channel/$CHANNEL_ID</uri></author>
        <entry>
          <yt:videoId>video-1</yt:videoId>
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
