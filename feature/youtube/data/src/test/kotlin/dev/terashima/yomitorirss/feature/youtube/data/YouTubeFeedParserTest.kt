package dev.terashima.yomitorirss.feature.youtube.data

import org.junit.Assert.assertEquals
import org.junit.Test

class YouTubeFeedParserTest {
  @Test
  fun `YouTube Atom feedをchannelとvideoへ変換できる`() {
    val xml = """
      <?xml version="1.0" encoding="UTF-8"?>
      <feed xmlns:yt="http://www.youtube.com/xml/schemas/2015"
            xmlns="http://www.w3.org/2005/Atom">
        <yt:channelId>_x5XG1OV2P6uZZ5FSM9Ttw</yt:channelId>
        <title>Google Developers</title>
        <entry>
          <id>yt:video:abc123</id>
          <yt:videoId>abc123</yt:videoId>
          <yt:channelId>UC_x5XG1OV2P6uZZ5FSM9Ttw</yt:channelId>
          <title>Sample video</title>
          <link rel="alternate" href="https://www.youtube.com/watch?v=abc123" />
          <published>2026-08-09T00:00:00+00:00</published>
        </entry>
      </feed>
    """.trimIndent().toByteArray()

    val result = YouTubeFeedParser().parse(xml)

    assertEquals("UC_x5XG1OV2P6uZZ5FSM9Ttw", result.channelId)
    assertEquals("Google Developers", result.channelTitle)
    assertEquals(1, result.videos.size)
    assertEquals("abc123", result.videos.single().id)
    assertEquals("UC_x5XG1OV2P6uZZ5FSM9Ttw", result.videos.single().channelId)
    assertEquals("Sample video", result.videos.single().title)
    assertEquals("https://www.youtube.com/watch?v=abc123", result.videos.single().url)
    assertEquals(1786233600000L, result.videos.single().publishedAtEpochMillis)
  }
}
