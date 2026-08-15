package dev.terashima.yomitorirss.feature.chat.data

import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.bookmark.BookmarkedArticle
import org.junit.Assert.assertEquals
import org.junit.Test

class AppResourceSkillsPolicyTest {
  @Test
  fun `共通検索toolはqueryだけをモデルへ公開する`() {
    assertEquals(listOf("query"), commonSearchArguments().map { it.name })
  }

  @Test
  fun `最近のブックマークは保存日時で新しい順にする`() {
    val old = bookmarkedArticle(id = "old", savedAt = "2026-08-14T00:00:00Z")
    val latest = bookmarkedArticle(id = "latest", savedAt = "2026-08-15T00:00:00Z")

    val sorted = recentBookmarks(listOf(old, latest))

    assertEquals(listOf("latest", "old"), sorted.map { it.article.id })
  }

  private fun bookmarkedArticle(id: String, savedAt: String): BookmarkedArticle = BookmarkedArticle(
    article = Article(
      id = id,
      feedId = null,
      externalId = null,
      identityKey = id,
      url = "https://example.com/$id",
      title = id,
      publishedAt = "2026-08-01T00:00:00Z",
      fetchedAt = "2026-08-01T00:00:00Z",
      readAt = null,
      sourceTitle = "example",
      sourceFeedUrl = "",
    ),
    savedAt = savedAt,
  )
}
