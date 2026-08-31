package dev.terashima.yomitorirss.feature.chat.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LexicalRetrievalTest {
  @Test
  fun `複数検索語は異なる項目に一致できる`() {
    val matching = TestDocument(title = "Android 17のAI実行", summary = "native memoryの調査")
    val missingTerm = TestDocument(title = "Android 17の変更", summary = "権限の説明")

    val ranked = rankByQuery(listOf(missingTerm, matching), "Android memory", ::fields)

    assertEquals(listOf(matching), ranked)
  }

  @Test
  fun `同じ検索語なら重要度が高い項目の一致を優先する`() {
    val summaryMatch = TestDocument(title = "開発メモ", summary = "RAGの設計")
    val titleMatch = TestDocument(title = "RAGの実装", summary = "検索について")

    val ranked = rankByQuery(listOf(summaryMatch, titleMatch), "RAG", ::fields)

    assertEquals(listOf(titleMatch, summaryMatch), ranked)
  }

  @Test
  fun `検索語が空なら元の順序を維持する`() {
    val first = TestDocument(title = "first", summary = "")
    val second = TestDocument(title = "second", summary = "")

    assertEquals(listOf(first, second), rankByQuery(listOf(first, second), "  ", ::fields))
  }

  @Test
  fun `候補用要約は空白を詰めて上限内に切り詰める`() {
    assertEquals("abc de…", compactExcerpt("abc   def ghi", 7))
  }

  private fun fields(document: TestDocument): List<RetrievalField> = listOf(
    RetrievalField(document.title, weight = 5),
    RetrievalField(document.summary, weight = 2),
  )

  private data class TestDocument(
    val title: String,
    val summary: String,
  )
}
