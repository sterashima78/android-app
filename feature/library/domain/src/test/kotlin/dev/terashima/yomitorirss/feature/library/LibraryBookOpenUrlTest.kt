package dev.terashima.yomitorirss.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryBookOpenUrlTest {
  @Test
  fun `Kindle ASIN から Kindle の書籍 URI を解決する`() {
    val book = LibraryBook(
      source = LibrarySource.KINDLE,
      sourceId = "B012345678",
      title = "Test Book",
      authors = emptyList(),
      publisher = null,
      publishedDate = null,
      description = null,
      isbn10 = null,
      isbn13 = null,
      thumbnailUrl = null,
      infoUrl = null,
    )

    assertEquals("kindle://book/?action=open&asin=B012345678", book.openUrl())
  }
}
