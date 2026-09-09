package dev.terashima.yomitorirss.feature.video.ui

import dev.terashima.yomitorirss.feature.video.WebVideoPlaybackDiagnostics
import dev.terashima.yomitorirss.feature.video.WebVideoPlaybackReferrerSource
import dev.terashima.yomitorirss.feature.video.WebVideoSecFetchSite
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoPlayerWebDiagnosticsTest {
  @Test
  fun `実request Cookieとnative参照元を値なしで表示する`() {
    val lines = webVideoPlaybackDiagnosticLines(
      diagnostics = WebVideoPlaybackDiagnostics(
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
        playbackReferrerSource = WebVideoPlaybackReferrerSource.OBSERVED_REQUEST,
      ),
      nativeRequestProperties = mapOf(
        "Referer" to "https://player.example.net/",
        "Origin" to "https://player.example.net",
      ),
    )

    assertEquals(
      listOf(
        "WebView stream request: 観測",
        "Cookie intercept: 対応",
        "Cookie: 実request",
        "WebView Referer: あり（path/queryあり）",
        "WebView Origin: なし",
        "Sec-Fetch-Site: cross-site",
        "再生参照元: WebView実request",
        "Native Referer: あり",
        "Native Origin: あり",
      ),
      lines,
    )
  }

  @Test
  fun `extractor指定参照元とnative header付与を表示する`() {
    val lines = webVideoPlaybackDiagnosticLines(
      diagnostics = WebVideoPlaybackDiagnostics(
        cookieSharingEnabled = true,
        cookieInterceptSupported = true,
        streamRequestObserved = false,
        profileCookieAvailable = true,
        playbackReferrerSource = WebVideoPlaybackReferrerSource.EXTRACTOR,
      ),
      nativeRequestProperties = mapOf(
        "Referer" to "https://player.example.net/",
        "Origin" to "https://player.example.net",
      ),
    )

    assertEquals("再生参照元: extractor指定", lines[6])
    assertEquals("Native Referer: あり", lines[7])
    assertEquals("Native Origin: あり", lines[8])
  }

  @Test
  fun `Cookie共有OFFは共有OFFとして表示する`() {
    val lines = webVideoPlaybackDiagnosticLines(
      WebVideoPlaybackDiagnostics(
        cookieSharingEnabled = false,
        cookieInterceptSupported = true,
        playbackReferrerSource = WebVideoPlaybackReferrerSource.PAGE,
      ),
    )

    assertEquals("Cookie: 共有OFF", lines[2])
    assertEquals("再生参照元: 元ページ", lines[6])
    assertEquals("Native Referer: なし", lines[7])
    assertEquals("Native Origin: なし", lines[8])
  }

  @Test
  fun `診断がなければ情報なしを表示する`() {
    assertEquals(listOf("Web診断: 情報なし"), webVideoPlaybackDiagnosticLines(null))
  }
}
