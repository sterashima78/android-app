package dev.terashima.yomitorirss.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test

class WebLibraryOpenRoutingTest {
  @Test
  fun `Web 蔵書の閲覧 URL だけを Custom Tabs 対象にする`() {
    val webUrl = "https://example.com/web-book"
    val books = listOf(
      book(LibrarySource.WEB, webUrl, webUrl),
      book(LibrarySource.AUDIBLE, "B000000000", "https://example.com/audible-book"),
      book(LibrarySource.GOOGLE_PLAY_BOOKS, "book-id", "https://example.com/google-book"),
      book(LibrarySource.WEB, "https://example.com/no-open-url", null),
    )

    assertEquals(setOf(webUrl), webLibraryOpenUrls(books))
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
