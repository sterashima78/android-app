package dev.terashima.yomitorirss.feature.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebLibraryThumbnailImageTest {
  @Test
  fun `表紙画像のRefererはページURLのoriginだけを使用する`() {
    assertEquals(
      "https://example.com/",
      webLibraryImageReferer("https://example.com/books/1?token=private#section"),
    )
  }

  @Test
  fun `非標準portは表紙画像のRefererに保持する`() {
    assertEquals(
      "https://example.com:8443/",
      webLibraryImageReferer("https://example.com:8443/books/1"),
    )
  }

  @Test
  fun `httpとhttps以外は表紙画像のRefererにしない`() {
    assertNull(webLibraryImageReferer("file:///tmp/cover.jpg"))
  }
}
