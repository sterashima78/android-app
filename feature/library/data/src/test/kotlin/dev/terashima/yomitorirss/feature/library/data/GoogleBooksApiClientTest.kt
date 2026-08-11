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
  fun `webReaderLink がなく infoLink だけの場合は読書 URL を持たない`() {
    val infoUrl = "https://play.google.com/store/books/details?id=library-volume"

    assertNull(googleBooksReadingUrl(null, infoUrl))
    assertNull(googleBooksReadingUrl("", infoUrl))
  }
}
