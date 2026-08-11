package dev.terashima.yomitorirss.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryRouteTest {
  @Test
  fun `Google Play Books の HTTP reader URL は HTTPS に正規化する`() {
    val url = "http://play.google.com/books/reader?id=volume-id&hl=ja&source=gbs_api"

    assertEquals(
      "https://play.google.com/books/reader?id=volume-id&hl=ja&source=gbs_api",
      normalizeGooglePlayBooksReaderUrl(url),
    )
  }

  @Test
  fun `Google Play Books の HTTPS reader URL は変更しない`() {
    val url = "https://play.google.com/books/reader?id=volume-id"

    assertEquals(url, normalizeGooglePlayBooksReaderUrl(url))
  }

  @Test
  fun `Google Books の情報 URL は変更しない`() {
    val url = "http://books.google.com/books?id=volume-id"

    assertEquals(url, normalizeGooglePlayBooksReaderUrl(url))
  }
}
