package dev.terashima.yomitorirss.feature.knowledge.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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

  @Test
  fun `保存日時が変わるとfingerprintが変わる`() {
    val before = buildKnowledgeTopics(listOf(source("a", savedAt = "2026-08-15T00:00:00Z"))).single()
    val after = buildKnowledgeTopics(listOf(source("a", savedAt = "2026-08-15T01:00:00Z"))).single()

    assertNotEquals(before.sourceFingerprint, after.sourceFingerprint)
  }

  @Test
  fun `正規化キーが同じでもトピック表示名が変わるとfingerprintが変わる`() {
    val before = buildKnowledgeTopics(listOf(source("a", tags = listOf("Android")))).single()
    val after = buildKnowledgeTopics(listOf(source("a", tags = listOf("ANDROID")))).single()

    assertEquals(before.id, after.id)
    assertNotEquals(before.sourceFingerprint, after.sourceFingerprint)
  }

  @Test
  fun `記事作成では依頼に関連する資料を優先する`() {
    val selected = selectKnowledgeSources(
      query = "Gemma 4 と Pixel 9 の実用性をまとめて",
      sources = listOf(
        source("a", title = "Gemma 4 on Android", summary = "Pixel 9 での推論速度"),
        source("b", title = "Kotlin Coroutines", summary = "非同期処理"),
      ),
      limit = 1,
    )

    assertEquals("a", selected.single().articleId)
  }

  @Test
  fun `派生記事では元記事の出典を優先する`() {
    val selected = selectKnowledgeSources(
      query = "別の観点でまとめて",
      sources = listOf(
        source("a", savedAt = "2026-08-14T00:00:00Z"),
        source("b", savedAt = "2026-08-15T00:00:00Z"),
      ),
      preferredArticleIds = setOf("a"),
      limit = 1,
    )

    assertEquals("a", selected.single().articleId)
  }

  @Test
  fun `既存出典が上限まであっても新しい関連資料の枠を確保する`() {
    val existing = (1..12).map { index ->
      source("old-$index", title = "既存資料 $index", summary = "以前の観点")
    }
    val newEvidence = source(
      "new",
      title = "Gemma 4 の新しい検証",
      summary = "Pixel 9 の実測データ",
      savedAt = "2026-08-16T00:00:00Z",
    )

    val selected = selectKnowledgeSources(
      query = "Gemma 4 と Pixel 9 の実測を追加して",
      sources = existing + newEvidence,
      preferredArticleIds = existing.mapTo(linkedSetOf()) { it.articleId },
      limit = 12,
    )

    assertEquals(12, selected.size)
    assertTrue(selected.any { it.articleId == "new" })
  }

  @Test
  fun `LLM出力のH1をタイトルとして本文から分離する`() {
    val document = parseGeneratedKnowledgeDocument(
      raw = "```markdown\n# Pixel 9 と Gemma 4\n\n## 概要\n本文 [1]\n```",
      fallbackTitle = "fallback",
    )

    assertEquals("Pixel 9 と Gemma 4", document.title)
    assertEquals("## 概要\n本文 [1]", document.bodyMarkdown)
  }

  @Test
  fun `H1がない場合は依頼から作ったタイトルを使う`() {
    val document = parseGeneratedKnowledgeDocument(
      raw = "## 概要\n本文",
      fallbackTitle = fallbackKnowledgeTitle("Gemma 4についてまとめて"),
    )

    assertEquals("Gemma 4", document.title)
    assertEquals("## 概要\n本文", document.bodyMarkdown)
  }

  private fun source(
    id: String,
    title: String = "title-$id",
    summary: String = "summary",
    tags: List<String> = emptyList(),
    folderName: String? = null,
    sourceTitle: String = "Feed",
    savedAt: String = "2026-08-15T00:00:00Z",
  ) = KnowledgeGenerationSource(
    articleId = id,
    title = title,
    url = "https://example.com/$id",
    sourceTitle = sourceTitle,
    savedAt = savedAt,
    summary = summary,
    tags = tags,
    folderName = folderName,
  )
}
