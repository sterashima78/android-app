package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibrarySource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ForegroundWebLibraryRenderedMetadataClientTest {
  @Test
  fun `前面Activityが利用可能になるまでWebView取得を開始しない`() = runBlocking {
    var availabilityChecks = 0
    var delegateCalled = false
    val delegate = object : WebLibraryRenderedMetadataClient {
      override suspend fun fetch(url: String, titleHint: String?): LibraryBook {
        delegateCalled = true
        return book(url)
      }
    }

    awaitWebLibraryForegroundAvailability(retryDelayMillis = 1L) {
      assertFalse(delegateCalled)
      availabilityChecks += 1
      availabilityChecks >= 3
    }

    delegate.fetch("https://example.com/books/1")
    assertEquals(3, availabilityChecks)
  }

  @Test
  fun `custom extractor判定はdelegateへそのまま委譲する`() {
    val delegate = object : WebLibraryRenderedMetadataClient {
      override suspend fun fetch(url: String, titleHint: String?): LibraryBook = book(url)
      override fun hasCustomExtractor(url: String): Boolean = url.endsWith("/custom")
    }

    assertEquals(true, delegate.hasCustomExtractor("https://example.com/custom"))
    assertEquals(false, delegate.hasCustomExtractor("https://example.com/normal"))
  }

  private fun book(url: String) = LibraryBook(
    source = LibrarySource.WEB,
    sourceId = url,
    title = "Example",
    authors = emptyList(),
    publisher = null,
    publishedDate = null,
    description = null,
    isbn10 = null,
    isbn13 = null,
    thumbnailUrl = null,
    infoUrl = url,
  )
}
