package dev.terashima.yomitorirss.feature.library.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebLibraryRenderedThumbnailFallbackTest {
  @Test
  fun `OGP画像がなければWebView内の先頭画像をサムネイルに使う`() {
    val payload = JSONObject()
      .put("url", "https://example.com/books/1")
      .put("title", "動的タイトル")
      .put("firstImage", "/images/first.jpg")
      .toString()

    val book = parseRenderedWebLibraryBook(
      requestedUrl = "https://example.com/start",
      rawResult = JSONObject.quote(payload),
    )

    assertEquals("https://example.com/images/first.jpg", book.thumbnailUrl)
  }

  @Test
  fun `OGP画像があればWebView内の先頭画像より優先する`() {
    val payload = JSONObject()
      .put("url", "https://example.com/books/1")
      .put("title", "動的タイトル")
      .put("image", "/images/ogp.jpg")
      .put("firstImage", "/images/first.jpg")
      .toString()

    val book = parseRenderedWebLibraryBook(
      requestedUrl = "https://example.com/start",
      rawResult = JSONObject.quote(payload),
    )

    assertEquals("https://example.com/images/ogp.jpg", book.thumbnailUrl)
  }

  @Test
  fun `OGP画像が安全でなければ安全な先頭画像へfallbackする`() {
    val payload = JSONObject()
      .put("url", "https://example.com/books/1")
      .put("title", "動的タイトル")
      .put("image", "http://cdn.example.com/ogp.jpg")
      .put("firstImage", "https://cdn.example.com/first.jpg")
      .toString()

    val book = parseRenderedWebLibraryBook(
      requestedUrl = "https://example.com/start",
      rawResult = JSONObject.quote(payload),
    )

    assertEquals("https://cdn.example.com/first.jpg", book.thumbnailUrl)
  }

  @Test
  fun `先頭画像もHTTPならサムネイルとして保存しない`() {
    val payload = JSONObject()
      .put("url", "https://example.com/books/1")
      .put("title", "動的タイトル")
      .put("firstImage", "http://cdn.example.com/first.jpg")
      .toString()

    val book = parseRenderedWebLibraryBook(
      requestedUrl = "https://example.com/start",
      rawResult = JSONObject.quote(payload),
    )

    assertNull(book.thumbnailUrl)
  }
}
