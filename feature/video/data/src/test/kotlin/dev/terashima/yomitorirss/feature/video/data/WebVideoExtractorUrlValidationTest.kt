package dev.terashima.yomitorirss.feature.video.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebVideoExtractorUrlValidationTest {
  @Test
  fun `抽出ページは標準portのHTTPSだけを許可する`() {
    assertTrue(isSafeExtractorPageUrl("https://example.com/watch/1"))
    assertTrue(isSafeExtractorPageUrl("https://example.com:443/watch/1"))
    assertFalse(isSafeExtractorPageUrl("http://example.com/watch/1"))
    assertFalse(isSafeExtractorPageUrl("https://example.com:8443/watch/1"))
    assertFalse(isSafeExtractorPageUrl("file:///tmp/video.html"))
  }

  @Test
  fun `thumbnailはrelative HTTPSを解決しHTTPを拒否する`() {
    assertEquals(
      "https://example.com/images/thumb.jpg",
      resolveWebVideoExtractorUrl(
        baseUrl = "https://example.com/watch/1",
        candidate = "/images/thumb.jpg",
        httpsOnly = true,
      ),
    )
    assertNull(
      resolveWebVideoExtractorUrl(
        baseUrl = "https://example.com/watch/1",
        candidate = "http://cdn.example.com/thumb.jpg",
        httpsOnly = true,
      ),
    )
  }

  @Test
  fun `streamはHTTPとHTTPSを許可し危険schemeを拒否する`() {
    assertEquals(
      "http://media.example.com/video.m3u8",
      resolveWebVideoExtractorUrl(
        baseUrl = "https://example.com/watch/1",
        candidate = "http://media.example.com/video.m3u8",
        httpsOnly = false,
      ),
    )
    assertEquals(
      "https://media.example.com/video.m3u8",
      resolveWebVideoExtractorUrl(
        baseUrl = "https://example.com/watch/1",
        candidate = "https://media.example.com/video.m3u8",
        httpsOnly = false,
      ),
    )
    assertNull(
      resolveWebVideoExtractorUrl(
        baseUrl = "https://example.com/watch/1",
        candidate = "javascript:alert(1)",
        httpsOnly = false,
      ),
    )
  }
}
