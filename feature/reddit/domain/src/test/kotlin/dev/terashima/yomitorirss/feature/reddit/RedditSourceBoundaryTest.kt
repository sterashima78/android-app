package dev.terashima.yomitorirss.feature.reddit

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedditSourceBoundaryTest {
  @Test
  fun `Reddit community と thread は RSS subscription input から除外する`() {
    assertFalse(RedditSourceBoundary.isRssSubscriptionInput("r/android"))
    assertFalse(
      RedditSourceBoundary.isRssSubscriptionInput(
        "https://www.reddit.com/r/android/comments/abc123/example/",
      ),
    )
    assertFalse(
      RedditSourceBoundary.isRssSubscriptionInput(
        "https://www.reddit.com/r/android/new/.rss",
      ),
    )
  }

  @Test
  fun `通常の RSS input と feed は Reddit 境界を通過する`() {
    val rss = "https://example.com/feed.xml"

    assertTrue(RedditSourceBoundary.isRssSubscriptionInput(rss))
    assertTrue(RedditSourceBoundary.isNonRedditFeed(rss))
  }

  @Test
  fun `Reddit feed は generic RSS presentation から除外する`() {
    assertFalse(
      RedditSourceBoundary.isNonRedditFeed(
        "https://www.reddit.com/r/android/new/.rss",
      ),
    )
  }
}
