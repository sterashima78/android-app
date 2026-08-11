package dev.terashima.yomitorirss.feature.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryRouteTest {
  @Test
  fun `Google Books の reader URL から Play Books reader URL を生成する`() {
    val url = "http://play.google.com/books/reader?id=volume-id&hl=ja&source=gbs_api"

    assertEquals(
      "https://play.google.com/books/reader?id=volume-id",
      googlePlayBooksReaderUrl(url),
    )
  }

  @Test
  fun `Google Books の情報 URL から Play Books reader URL を生成する`() {
    val url = "https://books.google.com/books?id=volume-id&printsec=frontcover"

    assertEquals(
      "https://play.google.com/books/reader?id=volume-id",
      googlePlayBooksReaderUrl(url),
    )
  }

  @Test
  fun `Google Play の書籍詳細 URL から Play Books reader URL を生成する`() {
    val url = "https://play.google.com/store/books/details/Title?id=volume-id"

    assertEquals(
      "https://play.google.com/books/reader?id=volume-id",
      googlePlayBooksReaderUrl(url),
    )
  }

  @Test
  fun `Volume ID がない URL は reader URL を生成しない`() {
    assertNull(googlePlayBooksReaderUrl("https://books.google.com/"))
  }
}
