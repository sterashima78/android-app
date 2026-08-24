package dev.terashima.yomitorirss.feature.reddit

import dev.terashima.yomitorirss.feature.article.Article
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
    assertFalse(RedditSourceBoundary.isRedditFeed(rss))
    assertTrue(RedditSourceBoundary.isNonRedditFeed(rss))
  }

  @Test
  fun `Reddit feed は owner boundary で Reddit と判定する`() {
    val redditFeed = "https://www.reddit.com/r/android/new/.rss"

    assertTrue(RedditSourceBoundary.isRedditFeed(redditFeed))
    assertFalse(RedditSourceBoundary.isNonRedditFeed(redditFeed))
  }

  @Test
  fun `Article は source feed に基づいて Reddit と generic RSS を分類する`() {
    val redditArticle = article("https://www.reddit.com/r/android/new/.rss")
    val rssArticle = article("https://example.com/feed.xml")

    assertTrue(RedditSourceBoundary.isRedditArticle(redditArticle))
    assertFalse(RedditSourceBoundary.isNonRedditArticle(redditArticle))
    assertFalse(RedditSourceBoundary.isRedditArticle(rssArticle))
    assertTrue(RedditSourceBoundary.isNonRedditArticle(rssArticle))
  }

  private fun article(sourceFeedUrl: String): Article = Article(
    id = "article",
    feedId = null,
    externalId = null,
    identityKey = "article",
    url = "https://example.com/article",
    title = "Article",
    publishedAt = "2026-08-24T00:00:00Z",
    fetchedAt = "2026-08-24T00:00:00Z",
    readAt = null,
    sourceTitle = "Source",
    sourceFeedUrl = sourceFeedUrl,
  )
}
