package dev.terashima.yomitorirss.feature.video.ui

import dev.terashima.yomitorirss.feature.video.VideoPlaybackTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoPlayerRequestPropertiesTest {
  @Test
  fun `Webストリームは元ページをRefererとして送る`() {
    val target = VideoPlaybackTarget.Stream(
      url = "https://cdn.example.com/video/master.m3u8",
      mimeType = "application/x-mpegURL",
      referrerUrl = "https://example.com/watch/1",
    )

    assertEquals(
      mapOf("Referer" to "https://example.com/watch/1"),
      webStreamRequestProperties(target),
    )
  }

  @Test
  fun `参照元がないWebストリームは追加headerを送らない`() {
    val target = VideoPlaybackTarget.Stream(
      url = "https://cdn.example.com/video/master.m3u8",
    )

    assertTrue(webStreamRequestProperties(target).isEmpty())
  }
}
