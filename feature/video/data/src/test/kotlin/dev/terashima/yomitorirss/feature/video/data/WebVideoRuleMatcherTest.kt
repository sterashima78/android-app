package dev.terashima.yomitorirss.feature.video.data

import dev.terashima.yomitorirss.feature.video.WebVideoExtractorRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebVideoRuleMatcherTest {
  @Test
  fun `より具体的なURLパターンを優先する`() {
    val rules = listOf(
      rule("wide", "https://example.com/*", 20),
      rule("specific", "https://example.com/videos/*", 10),
    )

    assertEquals(
      "specific",
      findMatchingWebVideoExtractorRule(rules, "https://example.com/videos/123")?.id,
    )
  }

  @Test
  fun `同じ具体度では新しいルールを優先する`() {
    val rules = listOf(
      rule("old", "https://example.com/watch/*", 10),
      rule("new", "https://example.com/watch/*", 20),
    )

    assertEquals(
      "new",
      findMatchingWebVideoExtractorRule(rules, "https://example.com/watch/123")?.id,
    )
  }

  @Test
  fun `HTTP URLにはカスタムルールを適用しない`() {
    assertNull(
      findMatchingWebVideoExtractorRule(
        listOf(rule("rule", "https://example.com/*", 10)),
        "http://example.com/video",
      ),
    )
  }

  private fun rule(id: String, pattern: String, updated: Long) = WebVideoExtractorRule(
    id = id,
    urlPattern = pattern,
    playbackExtractorCode = "async () => ({ streamUrl: null })",
    updatedAtEpochMillis = updated,
  )
}
