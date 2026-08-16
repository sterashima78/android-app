package dev.terashima.yomitorirss.feature.summary.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
  fun `中間要約の目標長はコンテキストに応じて増やす`() {
    assertEquals(240, HierarchicalSummaryText.intermediateTargetChars(300))
    assertEquals(400, HierarchicalSummaryText.intermediateTargetChars(4_096))
    assertEquals(600, HierarchicalSummaryText.intermediateTargetChars(8_192))
  }

  @Test
  fun `8192トークンでは2500文字を超える本文を直接入力できる`() {
    val prompt = "次の記事を要約してください。\n\n記事本文:\n{{article}}"

    val maxArticleChars = HierarchicalSummaryBudget.maxArticleChars(8_192, prompt)

    assertTrue(maxArticleChars > 5_000)
    assertTrue(HierarchicalSummaryBudget.fits(8_192, prompt, "あ".repeat(maxArticleChars)))
    assertFalse(HierarchicalSummaryBudget.fits(8_192, prompt, "あ".repeat(maxArticleChars + 1)))
  }

  @Test
  fun `長いプロンプトほど本文へ使える予算を減らす`() {
    val shortPrompt = "要約してください。\n{{article}}"
    val longPrompt = "要約条件です。".repeat(100) + "\n{{article}}"

    val shortBudget = HierarchicalSummaryBudget.maxArticleChars(8_192, shortPrompt)
    val longBudget = HierarchicalSummaryBudget.maxArticleChars(8_192, longPrompt)

    assertTrue(longBudget < shortBudget)
  }

  @Test
  fun `本文placeholderを複数使うプロンプトでは重複分も予算へ含める`() {
    val singlePrompt = "要約してください。\n{{article}}"
    val repeatedPrompt = "本文A:\n{{article}}\n\n本文B:\n{{article}}"

    val singleBudget = HierarchicalSummaryBudget.maxArticleChars(8_192, singlePrompt)
    val repeatedBudget = HierarchicalSummaryBudget.maxArticleChars(8_192, repeatedPrompt)

    assertTrue(repeatedBudget < singleBudget)
    assertTrue(HierarchicalSummaryBudget.fits(8_192, repeatedPrompt, "あ".repeat(repeatedBudget)))
    assertFalse(HierarchicalSummaryBudget.fits(8_192, repeatedPrompt, "あ".repeat(repeatedBudget + 1)))
  }

  @Test
  fun `文字数からトークン数を安全側に見積もる`() {
    assertEquals(0, HierarchicalSummaryBudget.estimatedTokens(""))
    assertEquals(6, HierarchicalSummaryBudget.estimatedTokens("12345"))
    assertEquals(12, HierarchicalSummaryBudget.estimatedTokens("あ".repeat(10)))
  }
}
