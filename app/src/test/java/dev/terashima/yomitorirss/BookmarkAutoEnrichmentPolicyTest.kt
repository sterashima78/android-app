package dev.terashima.yomitorirss

import dev.terashima.yomitorirss.feature.bookmark.data.BookmarkSourceMetadata
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkAutoEnrichmentPolicyTest {
  @Test
  fun `通常のRSS記事は自動AI処理の対象にする`() {
    assertTrue(
      shouldRequestBookmarkEnrichment(
        BookmarkSourceMetadata(
          url = "https://example.com/articles/1",
          sourceFeedUrl = "https://example.com/feed.xml",
        ),
      ),
    )
  }

  @Test
  fun `Redditフィード由来の記事は自動AI処理の対象外にする`() {
    assertFalse(
      shouldRequestBookmarkEnrichment(
        BookmarkSourceMetadata(
          url = "https://www.reddit.com/r/android/comments/abc123/example/",
          sourceFeedUrl = "https://www.reddit.com/r/android/new/.rss",
        ),
      ),
    )
  }

  @Test
  fun `共有されたRedditスレッドも自動AI処理の対象外にする`() {
    assertFalse(
      shouldRequestBookmarkEnrichment(
        BookmarkSourceMetadata(
          url = "https://www.reddit.com/r/android/comments/abc123/example/",
          sourceFeedUrl = "",
        ),
      ),
    )
  }

  @Test
  fun `YouTube動画は自動AI処理の対象外にする`() {
    listOf(
      "https://www.youtube.com/watch?v=abcdefghijk",
      "https://youtu.be/abcdefghijk",
      "https://www.youtube.com/shorts/abcdefghijk",
    ).forEach { url ->
      assertFalse(
        shouldRequestBookmarkEnrichment(
          BookmarkSourceMetadata(url = url, sourceFeedUrl = ""),
        ),
      )
    }
  }
}
