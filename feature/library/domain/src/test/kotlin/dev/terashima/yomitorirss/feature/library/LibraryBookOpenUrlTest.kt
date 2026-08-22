package dev.terashima.yomitorirss.feature.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

  @Test
  fun `SMB の内部 locator は外部 open URL として公開しない`() {
    val book = LibraryBook(
      source = LibrarySource.SMB,
      sourceId = "synthetic-source-id",
      title = "Synthetic Book",
      authors = emptyList(),
      publisher = null,
      publishedDate = null,
      description = null,
      isbn10 = null,
      isbn13 = null,
      thumbnailUrl = null,
      infoUrl = "yomitori://smb-book/open?sourceId=synthetic-source-id&serverId=synthetic-server&path=folder%5Cbook.pdf",
    )

    assertNull(book.openUrl())
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
