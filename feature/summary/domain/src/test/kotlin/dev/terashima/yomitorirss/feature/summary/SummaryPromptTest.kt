package dev.terashima.yomitorirss.feature.summary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryPromptTest {
  @Test
  fun `本文プレースホルダーを記事本文に置き換える`() {
    val rendered = renderSummaryPrompt("短く要約してください。\n$SUMMARY_ARTICLE_PLACEHOLDER", "記事本文")

    assertEquals("短く要約してください。\n記事本文", rendered)
  }

  @Test
  fun `プレースホルダーがない場合は末尾に記事本文を追加する`() {
    val rendered = renderSummaryPrompt("重要点を抽出してください。", "記事本文")

    assertEquals("重要点を抽出してください。\n\n記事本文:\n記事本文", rendered)
  }

  @Test
  fun `空のプロンプトを拒否する`() {
    val result = runCatching { normalizeSummaryPrompt("   ") }

    assertTrue(result.isFailure)
  }

  @Test
  fun `プロンプトが変わるとキャッシュキーも変わる`() {
    val first = summaryCacheKey("model", "3行で要約 $SUMMARY_ARTICLE_PLACEHOLDER")
    val second = summaryCacheKey("model", "1行で要約 $SUMMARY_ARTICLE_PLACEHOLDER")

    assertNotEquals(first, second)
  }

  @Test
  fun `Thinkingモードが変わるとキャッシュキーも変わる`() {
    val thinking = summaryCacheKey("model", "要約 $SUMMARY_ARTICLE_PLACEHOLDER", "think")
    val nonThinking = summaryCacheKey("model", "要約 $SUMMARY_ARTICLE_PLACEHOLDER", "no_think")

    assertNotEquals(thinking, nonThinking)
  }
}
