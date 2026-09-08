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
}
