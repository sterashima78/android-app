package dev.terashima.yomitorirss.feature.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFeatureRouteTest {
  @Test
  fun `Google Play Books の HTTP reader URL はパラメータを維持して HTTPS にする`() {
    val url = "http://play.google.com/books/reader?id=volume-id&hl=ja&source=gbs_api"

    assertEquals(
      "https://play.google.com/books/reader?id=volume-id&hl=ja&source=gbs_api",
      normalizeGooglePlayBooksReaderUrl(url),
    )
  }

  @Test
  fun `Google Play Books の HTTPS reader URL は変更しない`() {
    val url = "https://play.google.com/books/reader?id=volume-id&hl=ja"

    assertEquals(url, normalizeGooglePlayBooksReaderUrl(url))
  }

  @Test
  fun `Google Books の情報 URL は reader URL に変換しない`() {
    val url = "https://books.google.com/books?id=volume-id&printsec=frontcover"

    assertEquals(url, normalizeGooglePlayBooksReaderUrl(url))
  }

  @Test
  fun `Google Play Books の reader URL を読書リンクとして分類する`() {
    assertEquals(
      GoogleBooksLinkType.READER,
      googleBooksLinkType("https://play.google.com/books/reader?id=volume-id&hl=ja"),
    )
  }

  @Test
  fun `Google Play Books のホーム URL をアプリ起動先として分類する`() {
    assertEquals(
      GoogleBooksLinkType.PLAY_BOOKS_HOME,
      googleBooksLinkType("https://play.google.com/books"),
    )
    assertEquals(
      GoogleBooksLinkType.PLAY_BOOKS_HOME,
      googleBooksLinkType("https://play.google.com/books/"),
    )
  }

  @Test
  fun `Google Play の書籍詳細 URL は情報リンクとして分類する`() {
    assertEquals(
      GoogleBooksLinkType.INFORMATION,
      googleBooksLinkType("https://play.google.com/store/books/details?id=volume-id"),
    )
  }

  @Test
  fun `Google Books の情報 URL は情報リンクとして分類する`() {
    assertEquals(
      GoogleBooksLinkType.INFORMATION,
      googleBooksLinkType("https://books.google.com/books?id=volume-id"),
    )
  }

  @Test
  fun `Google Books 以外の URL は通常リンクとして分類する`() {
    assertEquals(
      GoogleBooksLinkType.OTHER,
      googleBooksLinkType("https://example.com/books?id=volume-id"),
    )
  }

  @Test
  fun `読書 Activity はストア Activity より優先する`() {
    val reader = readerActivityScore(
      "com.google.android.apps.play.books.ebook.activity.ReadingActivity",
    )
    val store = readerActivityScore(
      "com.google.android.apps.books.store.BookDetailActivity",
    )

    assertTrue(reader > 0)
    assertTrue(store < reader)
    assertTrue(store <= 0)
  }
}
