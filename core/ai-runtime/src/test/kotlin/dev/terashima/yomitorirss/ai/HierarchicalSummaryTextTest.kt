package dev.terashima.yomitorirss.core.airuntime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HierarchicalSummaryTextTest {
  @Test
  fun `長文は上限以下の複数チャンクへ先頭から順に分割する`() {
    val text = "第一文です。 第二文には重要な数値123があります。 第三文です。 第四文です。"

    val chunks = HierarchicalSummaryText.split(text, maxChars = 24)

    assertTrue(chunks.size > 1)
    assertTrue(chunks.all { it.length <= 24 })
    assertEquals(
      HierarchicalSummaryText.normalize(text),
      HierarchicalSummaryText.normalize(chunks.joinToString(" ")),
    )
  }

  @Test
  fun `中間要約のグループ化は順序と内容を維持する`() {
    val summaries = listOf(
      "前半の重要事項A",
      "中盤の重要事項B",
      "後半の重要事項C",
      "結論D",
    )

    val groups = HierarchicalSummaryText.pack(summaries, maxChars = 28)

    assertTrue(groups.size > 1)
    assertTrue(groups.all { HierarchicalSummaryText.join(it).length <= 28 })
    assertEquals(summaries, groups.flatten())
  }

  @Test
  fun `モデルごとの既存入力上限を分割単位として利用する`() {
    assertEquals(700, HierarchicalSummaryText.inputLimitFor("qwen2.5-0.5b-q8"))
    assertEquals(700, HierarchicalSummaryText.inputLimitFor("qwen2.5-1.5b-q8"))
    assertEquals(1_200, HierarchicalSummaryText.inputLimitFor("qwen3-4b-mixed-int4"))
    assertEquals(2_500, HierarchicalSummaryText.inputLimitFor("gemma4-e2b-it"))
    assertEquals(2_500, HierarchicalSummaryText.inputLimitFor("gemma4-e4b-it"))
  }

  @Test
  fun `未知モデルは最小の既存入力上限へフォールバックする`() {
    assertEquals(700, HierarchicalSummaryText.inputLimitFor("future-model"))
  }
}
