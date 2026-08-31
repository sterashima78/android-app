package dev.terashima.yomitorirss.feature.knowledge.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeTopicThresholdTest {
  @Test
  fun `3資料以上で使われるタグだけを自動Wikiトピックに昇格する`() {
    val topics = buildKnowledgeTopics(
      listOf(
        source("a", tags = listOf("Android", "単発")),
        source("b", tags = listOf("Android")),
        source("c", tags = listOf("Android")),
      ),
    )

    assertEquals(listOf("Android"), topics.map { it.title })
    assertEquals(3, topics.single().sources.size)
    assertFalse(topics.any { it.title == "単発" })
  }

  @Test
  fun `3資料未満のタグしかない資料はフォルダへフォールバックする`() {
    val topics = buildKnowledgeTopics(
      listOf(
        source("a", tags = listOf("細分化タグ"), folderName = "開発"),
        source("b", tags = listOf("細分化タグ"), folderName = "開発"),
      ),
    )

    assertEquals(1, topics.size)
    assertEquals("folder", topics.single().kind)
    assertEquals("開発", topics.single().title)
    assertEquals(2, topics.single().sources.size)
  }

  @Test
  fun `3資料未満のタグしかなくフォルダもない資料は提供元へフォールバックする`() {
    val topics = buildKnowledgeTopics(
      listOf(
        source("a", tags = listOf("単発A"), sourceTitle = "Example Feed"),
        source("b", tags = listOf("単発B"), sourceTitle = "Example Feed"),
      ),
    )

    assertEquals(1, topics.size)
    assertEquals("source", topics.single().kind)
    assertEquals("Example Feed", topics.single().title)
    assertEquals(2, topics.single().sources.size)
  }

  @Test
  fun `昇格タグを持つ資料は細分化タグがあってもフォールバックへ重複させない`() {
    val topics = buildKnowledgeTopics(
      listOf(
        source("a", tags = listOf("Android", "単発"), folderName = "開発"),
        source("b", tags = listOf("Android"), folderName = "開発"),
        source("c", tags = listOf("Android"), folderName = "開発"),
      ),
    )

    assertEquals(1, topics.size)
    assertEquals("tag", topics.single().kind)
    assertEquals("Android", topics.single().title)
    assertTrue(topics.single().sources.map { it.articleId }.containsAll(listOf("a", "b", "c")))
  }

  private fun source(
    id: String,
    tags: List<String>,
    folderName: String? = null,
    sourceTitle: String = "Feed",
  ) = KnowledgeGenerationSource(
    articleId = id,
    title = "title-$id",
    url = "https://example.com/$id",
    sourceTitle = sourceTitle,
    savedAt = "2026-09-01T00:00:00Z",
    summary = "summary",
    tags = tags,
    folderName = folderName,
  )
}
