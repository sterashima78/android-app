package dev.terashima.yomitorirss.feature.video.data

import dev.terashima.yomitorirss.feature.video.WebVideoSecFetchSite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebVideoRequestDiagnosticsTest {
  @Test
  fun `実requestの値を保持せず形状だけ診断する`() {
    val capture = WebVideoRequestDiagnosticsCapture()
    val requestUrl = "https://media.example.com/video/master.m3u8?session=fixture"

    capture.record(
      requestUrl,
      mapOf(
        "Referer" to "https://player.example.net/embed/123?mode=fixture",
        "Cookie" to "credential=fixture",
        "Sec-Fetch-Site" to "cross-site",
      ),
    )

    val diagnostics = capture.diagnosticsFor(requestUrl)
    assertTrue(diagnostics.streamRequestObserved)
    assertTrue(diagnostics.refererObserved)
    assertTrue(diagnostics.refererHasPathOrQuery)
    assertFalse(diagnostics.originObserved)
    assertNull(diagnostics.originMatchesReferrerOrigin)
    assertEquals(WebVideoSecFetchSite.CROSS_SITE, diagnostics.secFetchSite)
  }

  @Test
  fun `OriginとRefererのorigin一致だけを保持する`() {
    val capture = WebVideoRequestDiagnosticsCapture()
    val requestUrl = "https://media.example.com/video/master.m3u8"

    capture.record(
      requestUrl,
      mapOf(
        "Referer" to "https://player.example.net/embed/123",
        "Origin" to "https://player.example.net",
        "Sec-Fetch-Site" to "same-site",
      ),
    )

    val diagnostics = capture.diagnosticsFor(requestUrl)
    assertTrue(diagnostics.originObserved)
    assertEquals(true, diagnostics.originMatchesReferrerOrigin)
    assertEquals(WebVideoSecFetchSite.SAME_SITE, diagnostics.secFetchSite)
  }

  @Test
  fun `別stream URLの診断は流用しない`() {
    val capture = WebVideoRequestDiagnosticsCapture()
    capture.record(
      "https://media.example.com/video/other.m3u8",
      mapOf("Referer" to "https://player.example.net/"),
    )

    val diagnostics = capture.diagnosticsFor("https://media.example.com/video/master.m3u8")
    assertFalse(diagnostics.streamRequestObserved)
  }

  @Test
  fun `Cookie経路のbooleanだけをplayback診断へ投影する`() {
    val diagnostics = webVideoPlaybackDiagnostics(
      cookieSharingEnabled = true,
      cookieInterceptSupported = true,
      observed = ObservedWebVideoRequestDiagnostics(
        streamRequestObserved = true,
        refererObserved = true,
        originObserved = false,
        secFetchSite = WebVideoSecFetchSite.CROSS_SITE,
      ),
      streamRequestCookieObserved = true,
      profileCookieAvailable = true,
    )

    assertTrue(diagnostics.cookieSharingEnabled)
    assertTrue(diagnostics.cookieInterceptSupported)
    assertTrue(diagnostics.streamRequestCookieObserved)
    assertTrue(diagnostics.profileCookieAvailable)
    assertEquals(WebVideoSecFetchSite.CROSS_SITE, diagnostics.secFetchSite)
  }
}
