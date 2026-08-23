package dev.terashima.yomitorirss.feature.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryBookTapActionTest {
  @Test
  fun `SMB 書籍は内部 Book Reader を開く`() {
    val book = book(
      source = LibrarySource.SMB,
      sourceId = "synthetic-source-id",
      infoUrl = "yomitori://smb-book/open?sourceId=synthetic-source-id&serverId=synthetic-server&path=folder%5Cbook.pdf",
    )

    assertEquals(LibraryBookTapAction.OpenSmbBook, book.tapAction())
  }

  @Test
  fun `外部 URL を持つ書籍は URI handler を使う`() {
    val book = book(
      source = LibrarySource.GOOGLE_PLAY_BOOKS,
      sourceId = "synthetic-book-id",
      infoUrl = "https://example.com/book",
    )

    assertEquals(
      LibraryBookTapAction.OpenExternalUri("https://example.com/book"),
      book.tapAction(),
    )
  }

  @Test
  fun `開く先がない書籍は操作メニューを開く`() {
    val book = book(
      source = LibrarySource.GOOGLE_PLAY_BOOKS,
      sourceId = "synthetic-book-id",
      infoUrl = null,
    )

    assertEquals(LibraryBookTapAction.OpenMenu, book.tapAction())
  }

  @Test
  fun `Web 蔵書だけが蔵書から削除できる`() {
    assertTrue(
      book(
        source = LibrarySource.WEB,
        sourceId = "https://example.com/web-book",
        infoUrl = "https://example.com/web-book",
      ).canDeleteFromLibrary(),
    )
    assertFalse(
      book(
        source = LibrarySource.GOOGLE_PLAY_BOOKS,
        sourceId = "synthetic-book-id",
        infoUrl = "https://example.com/book",
      ).canDeleteFromLibrary(),
    )
  }

  private fun book(
    source: LibrarySource,
    sourceId: String,
    infoUrl: String?,
  ) = LibraryBook(
    source = source,
    sourceId = sourceId,
    title = "Synthetic Book",
    authors = emptyList(),
    publisher = null,
    publishedDate = null,
    description = null,
    isbn10 = null,
    isbn13 = null,
    thumbnailUrl = null,
    infoUrl = infoUrl,
  )
}