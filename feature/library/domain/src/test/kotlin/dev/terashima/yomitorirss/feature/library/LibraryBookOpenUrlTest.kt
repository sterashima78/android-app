package dev.terashima.yomitorirss.feature.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryBookOpenUrlTest {
  @Test
  fun `Kindle ASIN から Kindle の書籍 URI を解決する`() {
    val book = book(sourceId = "B012345678", title = "Test Book")

    assertEquals("kindle://book/?action=open&asin=B012345678", book.openUrl())
  }

  @Test
  fun `Personal Document はタイトル付きのアプリ内 URI に解決する`() {
    val book = book(
      sourceId = kindlePersonalDocumentSourceId("0123456789ABCDEF0123456789ABCDEF"),
      title = "検索する タイトル",
    )

    assertTrue(book.isKindlePersonalDocument())
    assertEquals(
      "yomitori://kindle-personal-document/open?title=%E6%A4%9C%E7%B4%A2%E3%81%99%E3%82%8B+%E3%82%BF%E3%82%A4%E3%83%88%E3%83%AB",
      book.openUrl(),
    )
  }

  private fun book(sourceId: String, title: String) = LibraryBook(
    source = LibrarySource.KINDLE,
    sourceId = sourceId,
    title = title,
    authors = emptyList(),
    publisher = null,
    publishedDate = null,
    description = null,
    isbn10 = null,
    isbn13 = null,
    thumbnailUrl = null,
    infoUrl = null,
  )
}
