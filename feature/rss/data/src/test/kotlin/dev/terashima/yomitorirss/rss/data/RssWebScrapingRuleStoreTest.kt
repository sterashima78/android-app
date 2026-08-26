package dev.terashima.yomitorirss.feature.rss.data

import dev.terashima.yomitorirss.feature.rss.RssWebScrapingRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RssWebScrapingRuleStoreTest {
  @Test
  fun `URLパターンはアスタリスクと疑問符をワイルドカードとして扱う`() {
    assertTrue(
      rssWebScrapingUrlPatternMatches(
        "https://example.com/series/*",
        "https://example.com/series/sample?view=all",
      ),
    )
    assertTrue(
      rssWebScrapingUrlPatternMatches(
        "https://example.com/series/?",
        "https://example.com/series/1",
      ),
    )
    assertFalse(
      rssWebScrapingUrlPatternMatches(
        "https://example.com/series/?",
        "https://example.com/series/12",
      ),
    )
  }

  @Test
  fun `HTTPの入力URLはHTTPSへ正規化してからルール照合する`() {
    assertEquals(
      "https://example.com/series/1",
      normalizeRssWebScrapingUrl("http://example.com/series/1"),
    )
    assertTrue(
      rssWebScrapingUrlPatternMatches(
        "https://example.com/series/*",
        "http://example.com/series/1",
      ),
    )
  }

  @Test
  fun `URLパターンはHTTPSのみ登録できる`() {
    val error = runCatching {
      validateRssWebScrapingRule(
        urlPattern = "http://example.com/series/*",
        functionCode = "async () => ({ title: 'sample', items: [] })",
      )
    }.exceptionOrNull()

    assertTrue(error is IllegalArgumentException)
  }

  @Test
  fun `タイムアウトは許容範囲外を拒否する`() {
    val tooShort = runCatching {
      validateRssWebScrapingRule(
        urlPattern = "https://example.com/series/*",
        functionCode = "async () => ({ title: 'sample', items: [] })",
        timeoutSeconds = 4,
      )
    }.exceptionOrNull()
    val tooLong = runCatching {
      validateRssWebScrapingRule(
        urlPattern = "https://example.com/series/*",
        functionCode = "async () => ({ title: 'sample', items: [] })",
        timeoutSeconds = 121,
      )
    }.exceptionOrNull()

    assertTrue(tooShort is IllegalArgumentException)
    assertTrue(tooLong is IllegalArgumentException)
  }

  @Test
  fun `複数ルールが一致する場合はより具体的なURLパターンを優先する`() {
    val generic = rule("generic", "https://example.com/*", updatedAt = 20L)
    val specific = rule("specific", "https://example.com/series/*", updatedAt = 10L)

    val result = findMatchingRssWebScrapingRule(
      listOf(generic, specific),
      "https://example.com/series/1",
    )

    assertEquals("specific", result?.id)
  }

  @Test
  fun `同じ具体度なら更新日時が新しいURLパターンを優先する`() {
    val old = rule("old", "https://example.com/*1", updatedAt = 10L)
    val new = rule("new", "https://example.com/a*", updatedAt = 20L)

    val result = findMatchingRssWebScrapingRule(
      listOf(old, new),
      "https://example.com/a1",
    )

    assertEquals("new", result?.id)
  }

  private fun rule(id: String, pattern: String, updatedAt: Long) = RssWebScrapingRule(
    id = id,
    urlPattern = pattern,
    functionCode = "async () => ({ title: 'sample', items: [] })",
    updatedAt = updatedAt,
  )
}
