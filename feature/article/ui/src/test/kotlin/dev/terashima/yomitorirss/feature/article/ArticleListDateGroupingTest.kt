package dev.terashima.yomitorirss.feature.article

import org.junit.Assert.assertEquals
import org.junit.Test

class ArticleListDateGroupingTest {
  @Test
  fun `日付が途中で再登場しても入力順を保持する`() {
    val articles = listOf(
      article("high-score", "2026-08-11T09:00:00Z"),
      article("low-score", "2026-08-12T09:00:00Z"),
      article("same-day-as-high", "2026-08-11T11:00:00Z"),
    )

    assertEquals(
      listOf(
        listOf("high-score"),
        listOf("low-score"),
        listOf("same-day-as-high"),
      ),
      consecutiveDateGroups(articles).map { group -> group.second.map(Article::id) },
    )
  }

  @Test
  fun `隣接する同じ日付は一つの見出しにまとめる`() {
    val articles = listOf(
      article("first", "2026-08-11T09:00:00Z"),
      article("second", "2026-08-11T10:00:00Z"),
      article("third", "2026-08-12T10:00:00Z"),
    )

    assertEquals(
      listOf(listOf("first", "second"), listOf("third")),
      consecutiveDateGroups(articles).map { group -> group.second.map(Article::id) },
    )
  }

  private fun article(id: String, time: String) = Article(
    id = id,
    feedId = null,
    externalId = id,
    identityKey = id,
    url = "https://example.com/$id",
    title = id,
    publishedAt = time,
    fetchedAt = time,
    readAt = null,
    sourceTitle = id,
    sourceFeedUrl = "https://example.com/feed.xml",
  )
}
