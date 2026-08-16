package dev.terashima.yomitorirss.feature.summary.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HierarchicalSummaryTextTest {
  @Test
  fun `長文はトークン予算以下の複数チャンクへ先頭から順に分割する`() {
    val text = "第一文です。 第二文には重要な数値123があります。 第三文です。 第四文です。"
    val tokenCount = ::weightedTokenCount
    val chunks = HierarchicalSummaryText.split(text) { tokenCount(it) <= 24 }

    assertTrue(chunks.size > 1)
    assertTrue(chunks.all { tokenCount(it) <= 24 })
    assertEquals(
      HierarchicalSummaryText.normalize(text).filterNot(Char::isWhitespace),
      chunks.joinToString("").filterNot(Char::isWhitespace),
    )
  }

  @Test
  fun `中間要約のグループ化はトークン予算内で順序と内容を維持する`() {
    val summaries = listOf(
      "前半の重要事項A",
      "中盤の重要事項B",
      "後半の重要事項C",
      "結論D",
    )
    val tokenCount = ::weightedTokenCount
    val groups = HierarchicalSummaryText.pack(summaries) { tokenCount(it) <= 28 }

    assertTrue(groups.size > 1)
    assertTrue(groups.all { tokenCount(HierarchicalSummaryText.join(it)) <= 28 })
    assertEquals(summaries, groups.flatten())
  }

  @Test
  fun `中間要約の目標長はコンテキストに応じて増やす`() {
    assertEquals(240, HierarchicalSummaryText.intermediateTargetChars(300))
    assertEquals(400, HierarchicalSummaryText.intermediateTargetChars(4_096))
    assertEquals(600, HierarchicalSummaryText.intermediateTargetChars(8_192))
  }

  @Test
  fun `本文が実トークン予算へ収まるかをtokenizer結果で判定する`() {
    val prompt = "次の記事を要約してください。\n\n記事本文:\n{{article}}"
    val tokenCount: (String) -> Int = { (it.length + 1) / 2 }

    assertTrue(HierarchicalSummaryBudget.fits(8_192, prompt, "あ".repeat(10_000), tokenCount))
    assertFalse(HierarchicalSummaryBudget.fits(8_192, prompt, "あ".repeat(15_000), tokenCount))
  }

  @Test
  fun `長いプロンプトほど同じ本文が予算を超えやすい`() {
    val shortPrompt = "要約してください。\n{{article}}"
    val longPrompt = "要約条件です。".repeat(300) + "\n{{article}}"
    val article = "あ".repeat(13_000)
    val tokenCount: (String) -> Int = { (it.length + 1) / 2 }

    assertTrue(HierarchicalSummaryBudget.fits(8_192, shortPrompt, article, tokenCount))
    assertFalse(HierarchicalSummaryBudget.fits(8_192, longPrompt, article, tokenCount))
  }

  @Test
  fun `本文placeholderを複数使うプロンプトでは重複分も実トークン予算へ含める`() {
    val singlePrompt = "要約してください。\n{{article}}"
    val repeatedPrompt = "本文A:\n{{article}}\n\n本文B:\n{{article}}"
    val article = "あ".repeat(7_500)
    val tokenCount: (String) -> Int = { (it.length + 1) / 2 }

    assertTrue(HierarchicalSummaryBudget.fits(8_192, singlePrompt, article, tokenCount))
    assertFalse(HierarchicalSummaryBudget.fits(8_192, repeatedPrompt, article, tokenCount))
  }

  @Test
  fun `実トークン数には出力とruntimeの予約領域を加える`() {
    val contextTokens = 8_192
    val exactlyFits: (String) -> Int = { contextTokens - 768 - 256 }
    val oneOver: (String) -> Int = { contextTokens - 768 - 256 + 1 }

    assertTrue(HierarchicalSummaryBudget.fitsRendered(contextTokens, "prompt", exactlyFits))
    assertFalse(HierarchicalSummaryBudget.fitsRendered(contextTokens, "prompt", oneOver))
  }

  private fun weightedTokenCount(text: String): Int = text.sumOf { character ->
    when {
      character.isWhitespace() -> 0
      character.code < 128 -> 1
      else -> 2
    }
  }
}
