package dev.terashima.yomitorirss

import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibrarySource
import dev.terashima.yomitorirss.feature.library.WebLibraryMutator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class NotifyingWebLibraryMutatorTest {
  @Test
  fun `Web蔵書再取得はdelegateのrefreshを呼びバックアップ変更を通知する`() = runBlocking {
    val events = mutableListOf<String>()
    val delegate = object : WebLibraryMutator {
      override suspend fun addWebBook(url: String, titleHint: String?): LibraryBook {
        events += "library:add"
        return webBook()
      }

      override suspend fun refreshWebBook(book: LibraryBook): LibraryBook {
        events += "library:refresh"
        return book.copy(title = "Refreshed")
      }

      override suspend fun removeWebBook(book: LibraryBook) = Unit
    }
    val notifying = NotifyingWebLibraryMutator(delegate) { events += "changed" }

    val refreshed = notifying.refreshWebBook(webBook())

    assertEquals("Refreshed", refreshed.title)
    assertEquals(listOf("library:refresh", "changed"), events)
  }

  private fun webBook() = LibraryBook(
    source = LibrarySource.WEB,
    sourceId = "https://example.com/book",
    title = "Book",
    authors = emptyList(),
    publisher = null,
    publishedDate = null,
    description = null,
    isbn10 = null,
    isbn13 = null,
    thumbnailUrl = "https://example.com/cover.jpg",
    infoUrl = "https://example.com/book",
  )
}
