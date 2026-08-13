package dev.terashima.yomitorirss.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryBookAudibleOpenUrlTest {
  @Test
  fun `Audible ASIN の既存 URL 解決を維持する`() {
    val book = LibraryBook(
      source = LibrarySource.AUDIBLE,
      sourceId = "A012345678",
      title = "Test Audio",
      authors = emptyList(),
      publisher = null,
      publishedDate = null,
      description = null,
      isbn10 = null,
      isbn13 = null,
      thumbnailUrl = null,
      infoUrl = null,
    )

    assertEquals("https://www.audible.co.jp/pd/A012345678", book.openUrl())
  }
}
