package dev.terashima.yomitorirss.feature.rss

import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.bookmark.BookmarkedArticle
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadLaterDisplayedAtTest {
  @Test
  fun `あとで読むの表示日時にはブックマーク日時を使う`() {
    val bookmark = BookmarkedArticle(
      article = Article(
        id = "article-1",
        feedId = null,
        externalId = null,
        identityKey = "article-1",
        url = "https://example.invalid/article-1",
        title = "article-1",
        publishedAt = "2026-09-01T00:00:00Z",
        fetchedAt = "2026-09-01T00:00:00Z",
        readAt = null,
        sourceTitle = "source",
        sourceFeedUrl = "https://example.invalid/feed",
      ),
      savedAt = "2026-09-15T12:34:56Z",
    )

    assertEquals(
      mapOf("article-1" to "2026-09-15T12:34:56Z"),
      readLaterDisplayedAtByArticleId(listOf(bookmark)),
    )
  }
}
