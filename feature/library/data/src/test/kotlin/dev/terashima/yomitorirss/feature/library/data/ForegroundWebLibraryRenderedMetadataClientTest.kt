package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibrarySource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundWebLibraryRenderedMetadataClientTest {
  @Test
  fun `前面Activityが利用可能になるまで待機する`() = runBlocking {
    var availabilityChecks = 0
    var operationStarted = false

    awaitWebLibraryForegroundAvailability(retryDelayMillis = 1L) {
      assertFalse(operationStarted)
      availabilityChecks += 1
      availabilityChecks >= 3
    }
    operationStarted = true

    assertTrue(operationStarted)
    assertEquals(3, availabilityChecks)
  }

  @Test
  fun `custom extractor判定はdelegateへそのまま委譲する`() {
    val delegate = object : WebLibraryRenderedMetadataClient {
      override suspend fun fetch(url: String, titleHint: String?): LibraryBook = book(url)
      override fun hasCustomExtractor(url: String): Boolean = url.endsWith("/custom")
    }
    val client = ForegroundWebLibraryRenderedMetadataClient(
      delegate = delegate,
      activityProvider = { null },
    )

    assertTrue(client.hasCustomExtractor("https://example.com/custom"))
    assertFalse(client.hasCustomExtractor("https://example.com/normal"))
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
