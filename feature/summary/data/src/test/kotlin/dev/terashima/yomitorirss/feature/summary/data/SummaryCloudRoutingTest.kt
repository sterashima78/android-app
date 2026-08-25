package dev.terashima.yomitorirss.feature.summary.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryCloudRoutingTest {
  @Test
  fun `cloud summary prompt asks Codex to open the exact URL and avoids fallback inference`() {
    val url = "https://example.com/articles/42"
    val prompt = "記事を要約してください。\n本文: {{article}}"

    val rendered = buildCloudSummaryPrompt(url, prompt)

    assertTrue(rendered.contains(url))
    assertTrue(rendered.contains("web_search"))
    assertTrue(rendered.contains("open_page"))
    assertTrue(rendered.contains("検索結果の断片・別ページ・事前知識から推測して要約しない"))
    assertFalse(rendered.contains("{{article}}"))
  }
}
