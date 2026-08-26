package dev.terashima.yomitorirss.feature.rss.data.network

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebScrapingFeedClientTest {
  @Test
  fun `取得結果から相対URLを解決してフィードプレビューを作る`() {
    val payload = JSONObject()
      .put("title", " サンプルフィード ")
      .put("siteUrl", "/series/sample")
      .put(
        "items",
        JSONArray()
          .put(
            JSONObject()
              .put("title", " 第1話 ")
              .put("url", "/series/sample/1")
              .put("externalId", "episode-1")
              .put("publishedAt", "2026-08-26T00:00:00Z"),
          )
          .put(
            JSONObject()
              .put("title", "第1話の重複")
              .put("url", "/series/sample/1"),
          ),
      )
      .toString()

    val result = parseWebScrapingPreview("https://example.com/series/sample", payload)

    assertEquals("サンプルフィード", result.title)
    assertEquals("https://example.com/series/sample", result.siteUrl)
    assertEquals(1, result.items.size)
    assertEquals("第1話", result.items.single().title)
    assertEquals("https://example.com/series/sample/1", result.items.single().url)
    assertEquals("episode-1", result.items.single().externalId)
    assertEquals("2026-08-26T00:00:00Z", result.items.single().publishedAt)
  }

  @Test
  fun `取得結果のHTTP記事URLは拒否する`() {
    val payload = JSONObject()
      .put("title", "サンプル")
      .put(
        "items",
        JSONArray().put(
          JSONObject()
            .put("title", "第1話")
            .put("url", "http://example.com/series/sample/1"),
        ),
      )
      .toString()

    val error = runCatching {
      parseWebScrapingPreview("https://example.com/series/sample", payload)
    }.exceptionOrNull()

    assertTrue(error is IllegalArgumentException)
  }

  @Test
  fun `Promise完了状態から取得データを読み取る`() {
    val value = JSONObject()
      .put("title", "サンプル")
      .put(
        "items",
        JSONArray().put(
          JSONObject()
            .put("title", "第1話")
            .put("url", "/series/sample/1"),
        ),
      )
      .toString()
    val pollPayload = JSONObject()
      .put("pending", false)
      .put("status", "applied")
      .put("value", value)
      .toString()

    val poll = parseWebScrapingPoll(
      finalUrl = "https://example.com/series/sample",
      rawResult = JSONObject.quote(pollPayload),
    )

    assertFalse(poll?.pending ?: true)
    assertNull(poll?.message)
    assertEquals("サンプル", poll?.preview?.title)
    assertEquals("https://example.com/series/sample/1", poll?.preview?.items?.single()?.url)
  }

  @Test
  fun `Promise rejectの理由をテスト結果へ返す`() {
    val pollPayload = JSONObject()
      .put("pending", false)
      .put("status", "rejected")
      .put("value", JSONObject.NULL)
      .put("message", "selector failed")
      .toString()

    val poll = parseWebScrapingPoll(
      finalUrl = "https://example.com/series/sample",
      rawResult = JSONObject.quote(pollPayload),
    )

    assertFalse(poll?.pending ?: true)
    assertNull(poll?.preview)
    assertEquals("selector failed", poll?.message)
  }

  @Test
  fun `取得結果の検証失敗理由をテスト結果へ返す`() {
    val value = JSONObject()
      .put("title", "サンプル")
      .put(
        "items",
        JSONArray().put(
          JSONObject()
            .put("title", "第1話")
            .put("url", "http://example.com/series/sample/1"),
        ),
      )
      .toString()
    val pollPayload = JSONObject()
      .put("pending", false)
      .put("status", "applied")
      .put("value", value)
      .toString()

    val poll = parseWebScrapingPoll(
      finalUrl = "https://example.com/series/sample",
      rawResult = JSONObject.quote(pollPayload),
    )

    assertFalse(poll?.pending ?: true)
    assertNull(poll?.preview)
    assertEquals("取得結果の URL は HTTPS の標準ポートである必要があります", poll?.message)
  }

  @Test
  fun `取得スクリプトはPromiseとして非同期実行される`() {
    val functionCode = "async ({ url }) => ({ title: url, items: [] });"
    val script = webScrapingStartScript(functionCode, "test-state")
    val pollScript = webScrapingPollScript("test-state")

    assertTrue(script.contains("const source = ${JSONObject.quote(functionCode.removeSuffix(";"))}"))
    assertTrue(script.contains("setTimeout(() => {"))
    assertTrue(script.contains("extractor = eval('(' + source + ')')"))
    assertTrue(script.contains("const promise = extractor({ url: location.href })"))
    assertTrue(script.contains("typeof promise.then !== 'function'"))
    assertTrue(script.contains("Array.isArray(value.items)"))
    assertTrue(script.contains("finish('rejected'"))
    assertTrue(script.contains("finish('threw'"))
    assertTrue(pollScript.contains("if (state.pending)"))
    assertTrue(pollScript.contains("delete window[stateKey]"))
  }
}
