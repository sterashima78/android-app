package dev.terashima.yomitorirss.feature.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibrarySeriesMergeTest {
  @Test
  fun `異なる取得元のシリーズをマージ先の手動シリーズへ統合する`() {
    val targetBook = book(
      source = LibrarySource.KINDLE,
      id = "KINDLE-1",
      title = "対象 1",
      series = LibrarySeries("統合先", 1, id = "SERIES-001"),
    )
    val sourceBook = book(
      source = LibrarySource.SMB,
      id = "SMB-2",
      title = "別表記 2",
      series = LibrarySeries("別表記", 2),
    )
    val target = LibrarySeriesSection(
      key = "id:SERIES-001",
      name = "統合先",
      books = listOf(targetBook),
    )
    val source = LibrarySeriesSection(
      key = "name:別表記",
      name = "別表記",
      books = listOf(sourceBook),
    )

    val updates = mergeLibrarySeries(source, target)

    assertEquals(listOf(targetBook, sourceBook), updates.map { it.book })
    assertEquals(listOf("統合先", "統合先"), updates.map { it.series.name })
    assertEquals(listOf(1, 2), updates.map { it.series.position })
    updates.forEach { assertNull(it.series.id) }
  }

  private fun book(
    source: LibrarySource,
    id: String,
    title: String,
    series: LibrarySeries,
  ) = LibraryBook(
    source = source,
    sourceId = id,
    title = title,
    authors = emptyList(),
    publisher = null,
    publishedDate = null,
    description = null,
    isbn10 = null,
    isbn13 = null,
    thumbnailUrl = null,
    infoUrl = null,
    series = series,
  )
}
