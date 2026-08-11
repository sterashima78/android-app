package dev.terashima.yomitorirss.feature.bookmark

import dev.terashima.yomitorirss.feature.article.Article
import org.junit.Assert.assertEquals
import org.junit.Test

class TagManagerScreenTest {
  private val androidTag = tag("android", "Android")
  private val aiTag = tag("ai", "AI")

  @Test
  fun `タグごとの記事数を集計する`() {
    val bookmarks = listOf(
      bookmark("1", androidTag),
      bookmark("2", androidTag, aiTag),
      bookmark("3", aiTag),
    )

    assertEquals(
      mapOf(androidTag.id to 2, aiTag.id to 2),
      countArticlesByTag(bookmarks),
    )
  }

  @Test
  fun `選択したタグの記事だけを取得する`() {
    val bookmarks = listOf(
      bookmark("1", androidTag),
      bookmark("2", androidTag, aiTag),
      bookmark("3", aiTag),
    )

    assertEquals(
      listOf("1", "2"),
      articlesWithTag(bookmarks, androidTag.id).map { it.article.id },
    )
  }

  @Test
  fun `非表示中の記事は件数と一覧の両方から除外する`() {
    val bookmarks = listOf(
      bookmark("1", androidTag),
      bookmark("2", androidTag),
    )
    val hidden = setOf("1")

    assertEquals(1, countArticlesByTag(bookmarks, hidden)[androidTag.id])
    assertEquals(
      listOf("2"),
      articlesWithTag(bookmarks, androidTag.id, hidden).map { it.article.id },
    )
  }

  private fun tag(id: String, name: String) = Tag(
    id = id,
    name = name,
    normalizedName = name.lowercase(),
    createdAt = "2026-08-11T00:00:00Z",
  )

  private fun bookmark(id: String, vararg tags: Tag) = BookmarkedArticle(
    article = Article(
      id = id,
      feedId = "feed",
      externalId = id,
      identityKey = "identity-$id",
      url = "https://example.com/$id",
      title = "Article $id",
      publishedAt = "2026-08-11T00:00:00Z",
      fetchedAt = "2026-08-11T00:00:00Z",
      readAt = null,
      sourceTitle = "Example",
      sourceFeedUrl = "https://example.com/feed.xml",
    ),
    savedAt = "2026-08-11T00:00:00Z",
    tags = tags.toList(),
  )
}
