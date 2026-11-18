package dev.terashima.yomitorirss.feature.rss

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetiredFeedSourcesTest {
  @Test
  fun `retired host feed URLs are recognized without matching lookalike hosts`() {
    assertTrue(isRetiredRedditFeedUrl("https://www.reddit.com/r/example/new/.rss"))
    assertTrue(isRetiredRedditFeedUrl("https://old.reddit.com/comments/abc/.rss"))
    assertFalse(isRetiredRedditFeedUrl("https://reddit.com.evil.example/feed"))
    assertFalse(isRetiredRedditFeedUrl("https://example.com/rss.xml"))
    assertFalse(isRetiredRedditFeedUrl("r/example"))
  }
}
