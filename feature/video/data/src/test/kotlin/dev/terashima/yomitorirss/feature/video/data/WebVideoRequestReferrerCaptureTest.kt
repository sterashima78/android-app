package dev.terashima.yomitorirss.feature.video.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebVideoRequestReferrerCaptureTest {
  @Test
  fun `実リクエストのRefererはoriginだけ保持する`() {
    val capture = WebVideoRequestReferrerCapture()

    capture.record(
      requestUrl = "https://media.example.com/video/master.m3u8?session=fixture#ignored",
      requestHeaders = mapOf(
        "referer" to "https://player.example.net/embed/123?mode=fixture#player",
      ),
    )

    assertEquals(
      "https://player.example.net/",
      capture.referrerFor("https://media.example.com/video/master.m3u8?session=fixture"),
    )
  }

  @Test
  fun `別リクエストのRefererはstreamへ流用しない`() {
    val capture = WebVideoRequestReferrerCapture()

    capture.record(
      requestUrl = "https://media.example.com/video/other.m3u8",
      requestHeaders = mapOf("Referer" to "https://player.example.net/embed/123"),
    )

    assertNull(capture.referrerFor("https://media.example.com/video/master.m3u8"))
  }

  @Test
  fun `既定HTTPS portの表記差は同じリクエストとして扱う`() {
    val capture = WebVideoRequestReferrerCapture()

    capture.record(
      requestUrl = "https://media.example.com:443/video/master.m3u8",
      requestHeaders = mapOf("Referer" to "https://player.example.net/embed/123"),
    )

    assertEquals(
      "https://player.example.net/",
      capture.referrerFor("https://media.example.com/video/master.m3u8"),
    )
  }

  @Test
  fun `Refererがなければ記録しない`() {
    val capture = WebVideoRequestReferrerCapture()

    capture.record(
      requestUrl = "https://media.example.com/video/master.m3u8",
      requestHeaders = mapOf("Accept" to "application/vnd.apple.mpegurl"),
    )

    assertNull(capture.referrerFor("https://media.example.com/video/master.m3u8"))
  }

  @Test
  fun `取得した参照元を元ページoriginより優先する`() {
    assertEquals(
      "https://player.example.net/",
      webStreamReferrerUrl(
        pageUrl = "https://page.example.com/watch/1",
        capturedReferrerUrl = "https://player.example.net/embed/123?mode=fixture#player",
      ),
    )
  }

  @Test
  fun `不正な取得参照元は元ページoriginへfallbackする`() {
    assertEquals(
      "https://page.example.com/",
      webStreamReferrerUrl(
        pageUrl = "https://page.example.com/watch/1",
        capturedReferrerUrl = "file:///tmp/player.html",
      ),
    )
  }
}
