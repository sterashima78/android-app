package dev.terashima.yomitorirss.feature.library.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoogleBooksApiClientTest {
  @Test
  fun `webReaderLink がある場合は読書 URL とする`() {
    val readerUrl = "http://play.google.com/books/reader?id=reader-volume&hl=ja"

    assertEquals(readerUrl, googleBooksReadingUrl(readerUrl))
  }

  @Test
  fun `webReaderLink がなく未購入の場合は読書 URL を返さない`() {
    assertNull(googleBooksReadingUrl(null, isPurchased = false))
    assertNull(googleBooksReadingUrl("", isPurchased = false))
  }

  @Test
  fun `webReaderLink がなく購入済みの場合は Play Books ホームへフォールバックする`() {
    assertEquals(
      GOOGLE_PLAY_BOOKS_HOME_URL,
      googleBooksReadingUrl(null, isPurchased = true),
    )
  }

  @Test
  fun `購入済みでも webReaderLink があれば reader URL を優先する`() {
    val readerUrl = "https://play.google.com/books/reader?id=purchased-reader"

    assertEquals(
      readerUrl,
      googleBooksReadingUrl(readerUrl, isPurchased = true),
    )
  }
}
