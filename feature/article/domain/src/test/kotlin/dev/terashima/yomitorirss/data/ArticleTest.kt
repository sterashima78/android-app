package dev.terashima.yomitorirss.feature.article

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArticleTest {
  @Test
  fun `記事はRSS由来の記事情報を保持する`() {
    val article = Article(
      id = "article-1",
      feedId = "feed-1",
      externalId = null,
      identityKey = "identity-1",
      url = "https://example.com/article",
      title = "Article",
      publishedAt = "2026-08-08T00:00:00Z",
      fetchedAt = "2026-08-08T00:01:00Z",
      readAt = null,
      sourceTitle = "Example",
      sourceFeedUrl = "https://example.com/feed.xml",
    )

    assertEquals("article-1", article.id)
    assertEquals("feed-1", article.feedId)
    assertEquals("https://example.com/article", article.url)
    assertEquals("Article", article.title)
    assertNull(article.readAt)
  }
}
