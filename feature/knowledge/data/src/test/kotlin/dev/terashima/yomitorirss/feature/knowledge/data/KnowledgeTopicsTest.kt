package dev.terashima.yomitorirss.feature.knowledge.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class KnowledgeTopicsTest {
  @Test
  fun `タグがある資料はタグ単位のトピックになる`() {
    val topics = buildKnowledgeTopics(
      listOf(
        source("a", tags = listOf("Android", "AI")),
        source("b", tags = listOf("Android")),
      ),
    )

    assertEquals(listOf("Android", "AI"), topics.map { it.title })
    assertEquals(2, topics.first { it.title == "Android" }.sources.size)
  }

  @Test
  fun `タグがない資料はフォルダを使いさらに無ければ提供元を使う`() {
    val topics = buildKnowledgeTopics(
      listOf(
        source("a", folderName = "開発"),
        source("b", sourceTitle = "Example Feed"),
      ),
    )

    assertEquals(setOf("開発", "Example Feed"), topics.map { it.title }.toSet())
  }

  @Test
  fun `要約が変わるとfingerprintが変わる`() {
    val before = buildKnowledgeTopics(listOf(source("a", summary = "before"))).single()
    val after = buildKnowledgeTopics(listOf(source("a", summary = "after"))).single()

    assertNotEquals(before.sourceFingerprint, after.sourceFingerprint)
    assertEquals(before.id, after.id)
  }

  private fun source(
    id: String,
    summary: String = "summary",
    tags: List<String> = emptyList(),
    folderName: String? = null,
    sourceTitle: String = "Feed",
  ) = KnowledgeGenerationSource(
    articleId = id,
    title = "title-$id",
    url = "https://example.com/$id",
    sourceTitle = sourceTitle,
    savedAt = "2026-08-15T00:00:00Z",
    summary = summary,
    tags = tags,
    folderName = folderName,
  )
}
