package dev.terashima.yomitorirss.feature.knowledge.data

import dev.terashima.yomitorirss.feature.knowledge.KnowledgePage
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgePageManagementTest {
  @Test
  fun `分割候補は前後に本文があるH2だけを返す`() {
    val body = """
      |導入
      |
      |## Android
      |Android本文 [1]
      |
      |## Kotlin
      |Kotlin本文 [2]
    """.trimMargin()

    assertEquals(listOf("Android", "Kotlin"), knowledgeSplitHeadings(body))
  }

  @Test
  fun `見出し位置で本文を二つの記事に分割する`() {
    val page = page(
      id = "primary",
      title = "AndroidとKotlin",
      body = "導入 [1]\n\n## Kotlin\nKotlin本文 [2]",
      sources = listOf(source(1, "a"), source(2, "b")),
    )

    val split = splitKnowledgePage(page, "Kotlin")

    assertEquals("導入 [1]", split.remainingBody)
    assertEquals("Kotlin", split.newTitle)
    assertEquals("Kotlin本文 [2]", split.newBody)
  }

  @Test
  fun `統合時に重複出典をまとめ引用番号を付け替える`() {
    val shared = source(1, "shared")
    val primary = page(
      id = "primary",
      title = "Android",
      body = "Android本文 [1]",
      sources = listOf(shared),
    )
    val secondary = page(
      id = "secondary",
      title = "Kotlin",
      body = "Kotlin本文 [1] と追加情報 [2]",
      sources = listOf(shared.copy(citationNumber = 1), source(2, "kotlin")),
    )

    val merged = mergeKnowledgePages(primary, secondary)

    assertEquals(listOf("shared", "kotlin"), merged.sources.map { it.articleId })
    assertTrue(merged.bodyMarkdown.contains("Android本文 [1]"))
    assertTrue(merged.bodyMarkdown.contains("Kotlin本文 [1] と追加情報 [2]"))
    assertTrue(merged.bodyMarkdown.contains("## Kotlin"))
  }

  @Test
  fun `統合元だけにある出典の番号は統合後の番号へ変換する`() {
    val primary = page(
      id = "primary",
      title = "先の記事",
      body = "先の記事 [1]",
      sources = listOf(source(1, "primary")),
    )
    val secondary = page(
      id = "secondary",
      title = "後の記事",
      body = "後の記事 [1]",
      sources = listOf(source(1, "secondary")),
    )

    val merged = mergeKnowledgePages(primary, secondary)

    assertTrue(merged.bodyMarkdown.contains("先の記事 [1]"))
    assertTrue(merged.bodyMarkdown.contains("後の記事 [2]"))
    assertFalse(merged.bodyMarkdown.contains("後の記事 [1]"))
  }

  private fun page(
    id: String,
    title: String,
    body: String,
    sources: List<KnowledgeSource>,
  ) = KnowledgePage(
    id = id,
    title = title,
    bodyMarkdown = body,
    sourceCount = sources.size,
    generatedAt = "2026-08-16T00:00:00Z",
    editorManaged = true,
    sources = sources,
  )

  private fun source(citation: Int, id: String) = KnowledgeSource(
    citationNumber = citation,
    articleId = id,
    title = "source-$id",
    url = "https://example.com/$id",
    sourceTitle = "Feed",
    savedAt = "2026-08-16T00:00:00Z",
  )
}
