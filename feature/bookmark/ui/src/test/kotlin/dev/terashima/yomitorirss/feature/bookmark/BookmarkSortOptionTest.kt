package dev.terashima.yomitorirss.feature.bookmark

import dev.terashima.yomitorirss.feature.article.Article
import org.junit.Assert.assertEquals
import org.junit.Test

class BookmarkSortOptionTest {
  private val olderArticle = bookmark(
    id = "older-article",
    publishedAt = "2026-09-01T00:00:00Z",
    savedAt = "2026-09-10T00:00:00Z",
  )
  private val newerArticle = bookmark(
    id = "newer-article",
    publishedAt = "2026-09-12T00:00:00Z",
    savedAt = "2026-09-05T00:00:00Z",
  )

  @Test
  fun `ブックマーク日の新しい順で並べる`() {
    assertEquals(
      listOf("older-article", "newer-article"),
      sortBookmarks(listOf(newerArticle, olderArticle), BookmarkSortOption.BOOKMARKED_NEWEST).ids(),
    )
  }

  @Test
  fun `ブックマーク日の古い順で並べる`() {
    assertEquals(
      listOf("newer-article", "older-article"),
      sortBookmarks(listOf(olderArticle, newerArticle), BookmarkSortOption.BOOKMARKED_OLDEST).ids(),
    )
  }

  @Test
  fun `記事公開日の新しい順で並べる`() {
    assertEquals(
      listOf("newer-article", "older-article"),
      sortBookmarks(listOf(olderArticle, newerArticle), BookmarkSortOption.PUBLISHED_NEWEST).ids(),
    )
  }

  @Test
  fun `記事公開日の古い順で並べる`() {
    assertEquals(
      listOf("older-article", "newer-article"),
      sortBookmarks(listOf(newerArticle, olderArticle), BookmarkSortOption.PUBLISHED_OLDEST).ids(),
    )
  }
}

private fun bookmark(
  id: String,
  publishedAt: String,
  savedAt: String,
): BookmarkedArticle = BookmarkedArticle(
  article = Article(
    id = id,
    feedId = null,
    externalId = null,
    identityKey = id,
    url = "https://example.invalid/$id",
    title = id,
    publishedAt = publishedAt,
    fetchedAt = publishedAt,
    readAt = null,
    sourceTitle = "source",
    sourceFeedUrl = "https://example.invalid/feed",
  ),
  savedAt = savedAt,
)

private fun List<BookmarkedArticle>.ids(): List<String> = map { it.article.id }
