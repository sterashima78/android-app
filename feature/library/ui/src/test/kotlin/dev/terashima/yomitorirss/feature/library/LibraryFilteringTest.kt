package dev.terashima.yomitorirss.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryFilteringTest {
  @Test
  fun `由来を指定しない場合は全蔵書を返す`() {
    val books = LibrarySource.entries.mapIndexed { index, source -> book(index.toString(), source) }

    assertEquals(books, filterLibraryBooksBySource(books, null))
  }

  @Test
  fun `指定したサービス由来の蔵書だけを返す`() {
    val books = listOf(
      book("google", LibrarySource.GOOGLE_PLAY_BOOKS),
      book("kindle", LibrarySource.KINDLE),
      book("audible", LibrarySource.AUDIBLE),
      book("kindle-2", LibrarySource.KINDLE),
    )

    assertEquals(
      listOf("kindle", "kindle-2"),
      filterLibraryBooksBySource(books, LibrarySource.KINDLE).map { it.sourceId },
    )
  }

  private fun book(id: String, source: LibrarySource) = LibraryBook(
    source = source,
    sourceId = id,
    title = id,
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
