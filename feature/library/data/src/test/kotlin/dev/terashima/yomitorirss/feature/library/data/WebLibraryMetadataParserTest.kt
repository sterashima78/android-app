package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.feature.library.LibrarySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebLibraryMetadataParserTest {
  @Test
  fun `OGPからWeb蔵書の書誌情報と表紙を生成する`() {
    val book = parseWebLibraryBook(
      url = "https://EXAMPLE.com/books/1#sample",
      html = """
        <html>
          <head>
            <meta content="漫画 &amp; 第1巻" property="og:title">
            <meta property="og:description" content="作品の説明">
            <meta content="/covers/1.jpg" property="og:image">
            <meta name="author" content="著者A, 著者B">
          </head>
        </html>
      """.trimIndent(),
    )

    assertEquals(LibrarySource.WEB, book.source)
    assertEquals("https://example.com/books/1", book.sourceId)
    assertEquals("https://example.com/books/1", book.infoUrl)
    assertEquals("漫画 & 第1巻", book.title)
    assertEquals("作品の説明", book.description)
    assertEquals(listOf("著者A", "著者B"), book.authors)
    assertEquals("https://example.com/covers/1.jpg", book.thumbnailUrl)
  }

  @Test
  fun `OGPがない場合はHTML titleを利用する`() {
    val book = parseWebLibraryBook(
      url = "https://example.com/book",
      html = "<html><head><title> Web &amp; Book </title></head></html>",
      titleHint = "共有タイトル",
    )

    assertEquals("Web & Book", book.title)
  }

  @Test
  fun `表紙URLがHTTPの場合は保存しない`() {
    val book = parseWebLibraryBook(
      url = "https://example.com/book",
      html = """
        <meta property="og:title" content="Book">
        <meta property="og:image" content="http://example.com/cover.jpg">
      """.trimIndent(),
    )

    assertNull(book.thumbnailUrl)
  }
}
