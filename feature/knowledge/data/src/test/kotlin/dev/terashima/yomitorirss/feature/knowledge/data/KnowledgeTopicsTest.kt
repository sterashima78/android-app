package dev.terashima.yomitorirss.feature.knowledge.data

import dev.terashima.yomitorirss.feature.knowledge.KnowledgePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
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
  fun `日本語の助詞でつながった依頼も個別の検索語として扱う`() {
    val selected = selectKnowledgeSources(
      query = "AndroidとKotlinの違いをまとめて",
      sources = listOf(
        source("android", title = "Androidアプリ設計", summary = "モバイルアプリの設計"),
        source("kotlin", title = "Kotlin入門", summary = "Kotlin言語の特徴"),
        source("rust", title = "Rust入門", summary = "所有権とライフタイム", savedAt = "2026-08-16T00:00:00Z"),
      ),
      limit = 2,
    )

    assertEquals(setOf("android", "kotlin"), selected.map { it.articleId }.toSet())
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

  @Test
  fun `編集プロンプトには記事本文の末尾まで含める`() {
    val body = "本文".repeat(2_000) + "末尾マーカー"
    val prompt = buildKnowledgeEditPrompt(
      page = page(body),
      instruction = "比較を追加して",
      sources = listOf(source("a")),
      promptBudgetChars = 12_000,
    )

    assertTrue(prompt.contains("末尾マーカー"))
  }

  @Test
  fun `全文が入力上限に収まらない記事は編集しない`() {
    val error = assertThrows(IllegalArgumentException::class.java) {
      buildKnowledgeEditPrompt(
        page = page("長文".repeat(4_000)),
        instruction = "短くして",
        sources = listOf(source("a")),
        promptBudgetChars = 8_000,
      )
    }

    assertTrue(error.message.orEmpty().contains("安全に全文編集"))
  }

  private fun page(body: String) = KnowledgePage(
    id = "page",
    title = "テスト記事",
    bodyMarkdown = body,
    sourceCount = 1,
    generatedAt = "2026-08-15T00:00:00Z",
    editorManaged = true,
    sources = emptyList(),
  )

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
