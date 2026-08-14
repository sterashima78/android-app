package dev.terashima.yomitorirss.feature.summary.data

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
      HierarchicalSummaryText.normalize(text).filterNot(Char::isWhitespace),
      chunks.joinToString("").filterNot(Char::isWhitespace),
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
  fun `中間要約の目標長は入力上限に応じて安全な範囲へ収める`() {
    assertEquals(180, HierarchicalSummaryText.intermediateTargetChars(300))
    assertEquals(400, HierarchicalSummaryText.intermediateTargetChars(2_500))
  }
}
