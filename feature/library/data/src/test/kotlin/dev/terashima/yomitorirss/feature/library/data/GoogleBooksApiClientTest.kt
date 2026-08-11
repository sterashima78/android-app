package dev.terashima.yomitorirss.feature.library.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoogleBooksApiClientTest {
  @Test
  fun `webReaderLink がある場合は infoLink より優先して読書 URL とする`() {
    val readerUrl = "http://play.google.com/books/reader?id=reader-volume&hl=ja"
    val infoUrl = "https://play.google.com/store/books/details?id=reader-volume"

    assertEquals(readerUrl, googleBooksReadingUrl(readerUrl, infoUrl))
  }

  @Test
  fun `webReaderLink がなく未購入の場合は infoLink を読書 URL にしない`() {
    val infoUrl = "https://play.google.com/store/books/details?id=library-volume"

    assertNull(googleBooksReadingUrl(null, infoUrl, isPurchased = false))
    assertNull(googleBooksReadingUrl("", infoUrl, isPurchased = false))
  }

  @Test
  fun `webReaderLink がなく購入済みの場合は Play Books ホームへフォールバックする`() {
    val infoUrl = "https://play.google.com/store/books/details?id=purchased-volume"

    assertEquals(
      GOOGLE_PLAY_BOOKS_HOME_URL,
      googleBooksReadingUrl(null, infoUrl, isPurchased = true),
    )
  }

  @Test
  fun `購入済みでも webReaderLink があれば reader URL を優先する`() {
    val readerUrl = "https://play.google.com/books/reader?id=purchased-reader"

    assertEquals(
      readerUrl,
      googleBooksReadingUrl(readerUrl, null, isPurchased = true),
    )
  }
}
