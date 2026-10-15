package dev.terashima.yomitorirss.feature.widget.data

import dev.terashima.yomitorirss.feature.article.Article
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetReadLaterPriorityFeedbackTest {
  @Test
  fun `RSS article found after save is registered exactly once`() = runBlocking {
    val seen = mutableListOf<String>()
    notifyWidgetReadLaterAdded(
      articleId = "rss-1",
      findArticle = { id ->
        seen += "find:$id"
        article("rss-1", "https://feeds.example.invalid/rss")
      },
      sourceSelector = { it.contains("rss") },
      onReadLaterSaved = { seen += "priority:${it.id}" },
    )

    assertEquals(listOf("find:rss-1", "priority:rss-1"), seen)
  }

  @Test
  fun `non-RSS articles cannot be registered by widget callback`() = runBlocking {
    val seen = mutableListOf<String>()
    notifyWidgetReadLaterAdded(
      articleId = "other-1",
      findArticle = { article("other-1", "https://feeds.example.invalid/other") },
      sourceSelector = { it.endsWith("/rss") },
      onReadLaterSaved = { seen += it.id },
    )

    assertEquals(emptyList<String>(), seen)
  }

  @Test
  fun `removed article cannot create a priority reference`() = runBlocking {
    val seen = mutableListOf<String>()
    notifyWidgetReadLaterAdded(
      articleId = "missing",
      findArticle = { null },
      sourceSelector = { true },
      onReadLaterSaved = { seen += it.id },
    )

    assertEquals(emptyList<String>(), seen)
  }

  private fun article(id: String, feed: String) = Article(
    id = id,
    feedId = null,
    externalId = id,
    identityKey = id,
    url = "https://example.invalid/$id",
    title = id,
    publishedAt = "2026-10-16T00:00:00Z",
    fetchedAt = "2026-10-16T00:00:00Z",
    readAt = null,
    sourceTitle = "test",
    sourceFeedUrl = feed,
  )
}
