package dev.terashima.yomitorirss.feature.reddit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RedditUrlsTest {
  @Test
  fun `subreddit shorthand is normalized to new RSS feed`() {
    assertEquals(
      "https://www.reddit.com/r/androiddev/new/.rss",
      redditCommunityFeedUrl("r/androiddev"),
    )
  }

  @Test
  fun `subreddit page is normalized to new RSS feed`() {
    assertEquals(
      "https://www.reddit.com/r/androiddev/new/.rss",
      redditCommunityFeedUrl("https://www.reddit.com/r/AndroidDev/"),
    )
  }

  @Test
  fun `thread permalink is normalized to comments RSS feed`() {
    val url = "https://www.reddit.com/r/androiddev/comments/1abc234/example_thread/"
    assertEquals("1abc234", redditThreadId(url))
    assertEquals(
      "https://www.reddit.com/r/androiddev/comments/1abc234/.rss",
      redditThreadFeedUrl(url),
    )
  }

  @Test
  fun `thread is not mistaken for community subscription`() {
    assertNull(redditCommunityFeedUrl("https://www.reddit.com/r/androiddev/comments/1abc234/example_thread/"))
  }

  @Test
  fun `Reddit feed URLs are classified separately`() {
    assertTrue(isRedditFeedUrl("https://www.reddit.com/r/androiddev/new/.rss"))
    assertTrue(isRedditFeedUrl("https://www.reddit.com/r/androiddev/comments/1abc234/.rss"))
    assertFalse(isRedditFeedUrl("https://example.com/feed.xml"))
  }
}
