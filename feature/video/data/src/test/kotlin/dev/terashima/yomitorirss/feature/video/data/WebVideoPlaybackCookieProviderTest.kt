package dev.terashima.yomitorirss.feature.video.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebVideoPlaybackCookieProviderTest {
  @Test
  fun `Cookie共有OFFではproviderを作成しない`() {
    var lookupCount = 0

    val provider = createPlaybackCookieProvider(enabled = false) {
      lookupCount += 1
      "a=b"
    }

    assertNull(provider)
    assertEquals(0, lookupCount)
  }

  @Test
  fun `Cookie共有ONではrequest URLをprofile cookie lookupへ渡す`() {
    var requestedUrl: String? = null
    val provider = createPlaybackCookieProvider(enabled = true) { url ->
      requestedUrl = url
      "a=b"
    }
    val requestUrl = "https://media.example.com/video/master.m3u8"

    assertEquals("a=b", provider?.cookieHeaderFor(requestUrl))
    assertEquals(requestUrl, requestedUrl)
  }

  @Test
  fun `実request Cookieがあればprofile lookupより優先する`() {
    var fallbackCount = 0
    val provider = createPlaybackCookieProvider(
      enabled = true,
      capturedCookieLookup = { "captured=value" },
      cookieLookup = {
        fallbackCount += 1
        "fallback=value"
      },
    )

    assertEquals(
      "captured=value",
      provider?.cookieHeaderFor("https://media.example.com/video/master.m3u8"),
    )
    assertEquals(0, fallbackCount)
  }

  @Test
  fun `実request Cookieがなければprofile lookupへfallbackする`() {
    val provider = createPlaybackCookieProvider(
      enabled = true,
      capturedCookieLookup = { null },
      cookieLookup = { "fallback=value" },
    )

    assertEquals(
      "fallback=value",
      provider?.cookieHeaderFor("https://media.example.com/video/master.m3u8"),
    )
  }

  @Test
  fun `HTTP以外のURLではCookieを問い合わせない`() {
    var lookupCount = 0
    val provider = createPlaybackCookieProvider(enabled = true) {
      lookupCount += 1
      "a=b"
    }

    assertNull(provider?.cookieHeaderFor("file:///tmp/video.m3u8"))
    assertNull(provider?.cookieHeaderFor("not a url"))
    assertEquals(0, lookupCount)
  }

  @Test
  fun `空のCookieは共有しない`() {
    val provider = createPlaybackCookieProvider(enabled = true) { "" }

    assertNull(provider?.cookieHeaderFor("https://media.example.com/video/master.m3u8"))
  }

  @Test
  fun `実request Cookie captureは共有OFFならheaderを保持しない`() {
    val capture = WebVideoRequestCookieCapture(enabled = false)
    val requestUrl = "https://media.example.com/video/master.m3u8"

    capture.record(requestUrl, mapOf("Cookie" to "captured=value"))

    assertNull(capture.cookieFor(requestUrl))
  }

  @Test
  fun `実request Cookieは完全一致したURLだけに利用する`() {
    val capture = WebVideoRequestCookieCapture(enabled = true)
    val requestUrl = "https://media.example.com/video/master.m3u8?session=fixture#ignored"

    capture.record(requestUrl, mapOf("cookie" to "captured=value"))

    assertEquals(
      "captured=value",
      capture.cookieFor("https://media.example.com/video/master.m3u8?session=fixture"),
    )
    assertNull(capture.cookieFor("https://media.example.com/video/segment.ts?session=fixture"))
    assertNull(capture.cookieFor("https://media.example.com/video/master.m3u8?session=other"))
  }

  @Test
  fun `実request Cookieの既定HTTPS port表記差は同じURLとして扱う`() {
    val capture = WebVideoRequestCookieCapture(enabled = true)

    capture.record(
      "https://media.example.com:443/video/master.m3u8",
      mapOf("Cookie" to "captured=value"),
    )

    assertEquals(
      "captured=value",
      capture.cookieFor("https://media.example.com/video/master.m3u8"),
    )
  }

  @Test
  fun `stream URL確定後は一致しないrequest Cookieを破棄する`() {
    val capture = WebVideoRequestCookieCapture(enabled = true)
    val streamUrl = "https://media.example.com/video/master.m3u8?session=fixture"
    val otherUrl = "https://player.example.net/config.json"

    capture.record(streamUrl, mapOf("Cookie" to "stream=value"))
    capture.record(otherUrl, mapOf("Cookie" to "other=value"))

    capture.retainOnly(streamUrl)

    assertEquals("stream=value", capture.cookieFor(streamUrl))
    assertNull(capture.cookieFor(otherUrl))
  }
}
