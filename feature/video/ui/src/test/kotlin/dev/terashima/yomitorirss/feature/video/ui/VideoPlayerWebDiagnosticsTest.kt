package dev.terashima.yomitorirss.feature.video.ui

import dev.terashima.yomitorirss.feature.video.WebVideoPlaybackDiagnostics
import dev.terashima.yomitorirss.feature.video.WebVideoSecFetchSite
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoPlayerWebDiagnosticsTest {
  @Test
  fun `実request CookieとOriginなしを値なしで表示する`() {
    val lines = webVideoPlaybackDiagnosticLines(
      WebVideoPlaybackDiagnostics(
        cookieSharingEnabled = true,
        cookieInterceptSupported = true,
        streamRequestObserved = true,
        streamRequestCookieObserved = true,
        profileCookieAvailable = true,
        streamRequestRefererObserved = true,
        streamRequestRefererHasPathOrQuery = true,
        streamRequestOriginObserved = false,
        streamRequestOriginMatchesReferrerOrigin = null,
        secFetchSite = WebVideoSecFetchSite.CROSS_SITE,
      ),
    )

    assertEquals(
      listOf(
        "WebView stream request: 観測",
        "Cookie intercept: 対応",
        "Cookie: 実request",
        "Referer: あり（path/queryあり）",
        "Origin: なし",
        "Sec-Fetch-Site: cross-site",
      ),
      lines,
    )
  }

  @Test
  fun `Cookie共有OFFは共有OFFとして表示する`() {
    val lines = webVideoPlaybackDiagnosticLines(
      WebVideoPlaybackDiagnostics(
        cookieSharingEnabled = false,
        cookieInterceptSupported = true,
      ),
    )

    assertEquals("Cookie: 共有OFF", lines[2])
  }

  @Test
  fun `診断がなければ情報なしを表示する`() {
    assertEquals(listOf("Web診断: 情報なし"), webVideoPlaybackDiagnosticLines(null))
  }
}
