package dev.terashima.yomitorirss.feature.widget.data

import dev.terashima.yomitorirss.feature.rss.Feed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class WidgetFeedRefreshTest {
  @Test
  fun cancellationIsPropagatedAndRemainingFeedsAreSkipped() = runBlocking {
    val visited = mutableListOf<String>()
    val cancellation = CancellationException("worker stopped")

    try {
      refreshWidgetFeeds(listOf(feed("one"), feed("two"))) { current ->
        visited += current.id
        throw cancellation
      }
      fail("Cancellation must propagate")
    } catch (actual: CancellationException) {
      assertSame(cancellation, actual)
    }

    assertEquals(listOf("one"), visited)
  }

  @Test
  fun ordinaryFeedFailureDoesNotSkipOtherFeeds() = runBlocking {
    val visited = mutableListOf<String>()

    refreshWidgetFeeds(listOf(feed("one"), feed("two"))) { current ->
      visited += current.id
      if (current.id == "one") error("temporary network failure")
    }

    assertEquals(listOf("one", "two"), visited)
  }

  private fun feed(id: String) = Feed(
    id = id,
    title = id,
    feedUrl = "https://example.invalid/${id}",
    siteUrl = null,
    etag = null,
    lastModified = null,
    lastFetchedAt = null,
    lastError = null,
    createdAt = "2026-10-01T00:00:00Z",
  )
}
