package dev.terashima.yomitorirss.core.airuntime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryPromptTest {
  @Test
  fun `本文プレースホルダーを記事本文に置き換える`() {
    val rendered = SummaryPrompt.render("短く要約してください。\n${SummaryPrompt.ARTICLE_PLACEHOLDER}", "記事本文")

    assertEquals("短く要約してください。\n記事本文", rendered)
  }

  @Test
  fun `プレースホルダーがない場合は末尾に記事本文を追加する`() {
    val rendered = SummaryPrompt.render("重要点を抽出してください。", "記事本文")

    assertEquals("重要点を抽出してください。\n\n記事本文:\n記事本文", rendered)
  }

  @Test
  fun `空のプロンプトを拒否する`() {
    val result = runCatching { SummaryPrompt.normalize("   ") }

    assertTrue(result.isFailure)
  }

  @Test
  fun `プロンプトが変わるとキャッシュキーも変わる`() {
    val first = SummaryPrompt.cacheKey("model", "3行で要約 ${SummaryPrompt.ARTICLE_PLACEHOLDER}")
    val second = SummaryPrompt.cacheKey("model", "1行で要約 ${SummaryPrompt.ARTICLE_PLACEHOLDER}")

    assertNotEquals(first, second)
  }
}
