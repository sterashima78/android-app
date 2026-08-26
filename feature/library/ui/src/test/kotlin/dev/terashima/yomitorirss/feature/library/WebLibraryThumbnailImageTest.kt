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

  @Test
  fun `Web表紙画像のrequest headerにはorigin Refererとbrowser User-Agentを含める`() {
    assertEquals(
      mapOf(
        "Referer" to "https://example.com/",
        "User-Agent" to "ExampleBrowser/1.0",
      ),
      webLibraryImageRequestHeaders(
        pageUrl = "https://example.com/books/1?token=private#section",
        browserUserAgent = "ExampleBrowser/1.0",
      ),
    )
  }

  @Test
  fun `Refererを生成できない場合もbrowser User-Agentは使用する`() {
    assertEquals(
      mapOf("User-Agent" to "ExampleBrowser/1.0"),
      webLibraryImageRequestHeaders(
        pageUrl = "file:///tmp/cover.jpg",
        browserUserAgent = "ExampleBrowser/1.0",
      ),
    )
  }

  @Test
  fun `空のbrowser User-Agentはrequest headerへ追加しない`() {
    assertEquals(
      mapOf("Referer" to "https://example.com/"),
      webLibraryImageRequestHeaders(
        pageUrl = "https://example.com/books/1",
        browserUserAgent = "  ",
      ),
    )
  }
}
