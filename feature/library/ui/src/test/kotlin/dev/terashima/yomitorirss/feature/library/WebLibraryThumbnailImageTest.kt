package dev.terashima.yomitorirss.feature.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebLibraryThumbnailImageTest {
  private val imageAccept = "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"

  @Test
  fun `表紙画像の最初のRefererはページURLのoriginだけを使用する`() {
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
  fun `fallback Refererはページpathを含めqueryとfragmentとuserinfoを除外する`() {
    assertEquals(
      "https://example.com/books/1",
      webLibraryImagePageReferer("https://user:secret@example.com/books/1?token=private#section"),
    )
  }

  @Test
  fun `Web表紙画像はoriginからpage pathへ段階的にRefererを広げる`() {
    assertEquals(
      listOf(
        mapOf(
          "Accept" to imageAccept,
          "User-Agent" to "ExampleBrowser/1.0",
          "Referer" to "https://example.com/",
        ),
        mapOf(
          "Accept" to imageAccept,
          "User-Agent" to "ExampleBrowser/1.0",
          "Referer" to "https://example.com/books/1",
        ),
      ),
      webLibraryImageRequestHeaderCandidates(
        pageUrl = "https://example.com/books/1?token=private#section",
        browserUserAgent = "ExampleBrowser/1.0",
      ),
    )
  }

  @Test
  fun `root pageでは同じReferer候補を重複させない`() {
    assertEquals(
      listOf(
        mapOf(
          "Accept" to imageAccept,
          "User-Agent" to "ExampleBrowser/1.0",
          "Referer" to "https://example.com/",
        ),
      ),
      webLibraryImageRequestHeaderCandidates(
        pageUrl = "https://example.com/",
        browserUserAgent = "ExampleBrowser/1.0",
      ),
    )
  }

  @Test
  fun `Refererを生成できない場合も画像Acceptとbrowser User-Agentは使用する`() {
    assertEquals(
      listOf(
        mapOf(
          "Accept" to imageAccept,
          "User-Agent" to "ExampleBrowser/1.0",
        ),
      ),
      webLibraryImageRequestHeaderCandidates(
        pageUrl = "file:///tmp/cover.jpg",
        browserUserAgent = "ExampleBrowser/1.0",
      ),
    )
  }

  @Test
  fun `空のbrowser User-Agentはrequest headerへ追加しない`() {
    assertEquals(
      mapOf(
        "Accept" to imageAccept,
        "Referer" to "https://example.com/",
      ),
      webLibraryImageRequestHeaders(
        pageUrl = "https://example.com/books/1",
        browserUserAgent = "  ",
      ),
    )
  }
}
