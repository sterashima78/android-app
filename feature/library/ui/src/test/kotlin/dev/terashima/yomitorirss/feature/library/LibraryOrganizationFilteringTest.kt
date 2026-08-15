package dev.terashima.yomitorirss.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryOrganizationFilteringTest {
  private val first = testBook("first", "Android設計入門")
  private val second = testBook("second", "Kotlin入門")

  @Test
  fun `未整理はタグとコレクションの両方がない本を返す`() {
    val snapshot = LibraryOrganizationSnapshot(
      items = mapOf(
        first.organizationKey() to LibraryItemOrganization(
          key = first.organizationKey(),
          tags = listOf(LibraryOrganizationTag("tag", "Android", "android")),
        ),
      ),
    )

    assertEquals(
      listOf(second),
      filterLibraryBooksForOrganization(
        listOf(first, second),
        snapshot,
        LibraryOrganizationFilter.UNORGANIZED,
      ),
    )
  }

  @Test
  fun `読書状態のスマート条件で本を絞り込める`() {
    val snapshot = LibraryOrganizationSnapshot(
      items = mapOf(
        first.organizationKey() to LibraryItemOrganization(
          key = first.organizationKey(),
          readingStatus = LibraryReadingStatus.READING,
        ),
        second.organizationKey() to LibraryItemOrganization(
          key = second.organizationKey(),
          readingStatus = LibraryReadingStatus.FINISHED,
        ),
      ),
    )

    assertEquals(
      listOf(first),
      filterLibraryBooksForOrganization(
        listOf(first, second),
        snapshot,
        LibraryOrganizationFilter.READING,
      ),
    )
  }
}

private fun testBook(sourceId: String, title: String): LibraryBook = LibraryBook(
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
