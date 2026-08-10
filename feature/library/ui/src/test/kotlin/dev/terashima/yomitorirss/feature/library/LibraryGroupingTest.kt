package dev.terashima.yomitorirss.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryGroupingTest {
  @Test
  fun `シリーズごとにまとめ巻数順で並べる`() {
    val books = listOf(
      book(id = "3", title = "単独"),
      book(id = "2", title = "第二巻", series = LibrarySeries("テストシリーズ", 2)),
      book(id = "1", title = "第一巻", series = LibrarySeries("テストシリーズ", 1)),
    )

    val groups = groupLibraryBooks(books)

    assertEquals(listOf("テストシリーズ"), groups.series.map { it.name })
    assertEquals(listOf("第一巻", "第二巻"), groups.series.single().books.map { it.title })
    assertEquals(listOf("単独"), groups.ungrouped.map { it.title })
  }

  @Test
  fun `巻数がない本は巻数指定済みの本より後ろに並べる`() {
    val books = listOf(
      book(id = "2", title = "番外編", series = LibrarySeries("シリーズ", null)),
      book(id = "1", title = "本編", series = LibrarySeries("シリーズ", 3)),
    )

    val groups = groupLibraryBooks(books)

    assertEquals(listOf("本編", "番外編"), groups.series.single().books.map { it.title })
  }

  private fun book(
    id: String,
    title: String,
    series: LibrarySeries? = null,
  ) = LibraryBook(
    source = LibrarySource.GOOGLE_PLAY_BOOKS,
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
