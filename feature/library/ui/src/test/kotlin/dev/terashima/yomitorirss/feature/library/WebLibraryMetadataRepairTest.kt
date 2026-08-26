package dev.terashima.yomitorirss.feature.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebLibraryMetadataRepairTest {
  @Test
  fun `表紙が未取得なら再取得対象にする`() {
    val book = webBook(title = "取得済みタイトル", thumbnailUrl = null)

    assertTrue(book.needsWebMetadataRepair())
    assertEquals(listOf("表紙"), book.missingWebMetadataLabels())
  }

  @Test
  fun `host名fallbackのタイトルなら再取得対象にする`() {
    val book = webBook(
      title = "example.com",
      thumbnailUrl = "https://example.com/cover.jpg",
    )

    assertTrue(book.needsWebMetadataRepair())
    assertEquals(listOf("タイトル"), book.missingWebMetadataLabels())
  }

  @Test
  fun `タイトルと表紙が取得済みなら再取得対象から除外する`() {
    val book = webBook(
      title = "取得済みタイトル",
      thumbnailUrl = "https://example.com/cover.jpg",
    )

    assertFalse(book.needsWebMetadataRepair())
    assertEquals(emptyList<String>(), book.missingWebMetadataLabels())
  }

  @Test
  fun `Web以外はmetadata再取得対象にしない`() {
    val book = webBook(
      title = "example.com",
      thumbnailUrl = null,
    ).copy(source = LibrarySource.KINDLE)

    assertFalse(book.needsWebMetadataRepair())
  }

  private fun webBook(
    title: String,
    thumbnailUrl: String?,
  ): LibraryBook = LibraryBook(
    source = LibrarySource.WEB,
    sourceId = "https://example.com/book",
    title = title,
    authors = emptyList(),
    publisher = null,
    publishedDate = null,
    description = null,
    isbn10 = null,
    isbn13 = null,
    thumbnailUrl = thumbnailUrl,
    infoUrl = "https://example.com/book",
  )
}
