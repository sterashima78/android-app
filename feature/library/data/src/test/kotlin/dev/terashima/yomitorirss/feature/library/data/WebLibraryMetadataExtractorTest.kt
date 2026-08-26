package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibrarySource
import dev.terashima.yomitorirss.feature.library.WebLibraryMetadataExtractor
import dev.terashima.yomitorirss.feature.library.WebLibraryMetadataExtractorExecution
import dev.terashima.yomitorirss.feature.library.WebLibraryMetadataExtractorStatus
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebLibraryMetadataExtractorTest {
  @Test
  fun `URLパターンはアスタリスクと疑問符をワイルドカードとして扱う`() {
    assertTrue(
      webLibraryUrlPatternMatches(
        "https://example.com/books/*",
        "https://example.com/books/123?view=full",
      ),
    )
    assertTrue(
      webLibraryUrlPatternMatches(
        "https://example.com/books/?",
        "https://example.com/books/1",
      ),
    )
    assertFalse(
      webLibraryUrlPatternMatches(
        "https://example.com/books/?",
        "https://example.com/books/12",
      ),
    )
    assertFalse(
      webLibraryUrlPatternMatches(
        "https://example.com/books/*",
        "http://example.com/books/1",
      ),
    )
  }

  @Test
  fun `URLパターンはHTTPSのみ登録できる`() {
    val error = runCatching {
      validateWebLibraryMetadataExtractor(
        urlPattern = "http://example.com/books/*",
        functionCode = "async () => ({ title: null, thumbnailUrl: null })",
      )
    }.exceptionOrNull()

    assertTrue(error is IllegalArgumentException)
  }

  @Test
  fun `空の関数コードは登録できない`() {
    val error = runCatching {
      validateWebLibraryMetadataExtractor(
        urlPattern = "https://example.com/books/*",
        functionCode = "   ",
      )
    }.exceptionOrNull()

    assertTrue(error is IllegalArgumentException)
  }

  @Test
  fun `複数ルールが一致する場合はより具体的なURLパターンを優先する`() {
    val generic = extractor(
      id = "generic",
      pattern = "https://example.com/*",
      updatedAt = 20L,
    )
    val specific = extractor(
      id = "specific",
      pattern = "https://example.com/books/*",
      updatedAt = 10L,
    )

    val result = findMatchingWebLibraryMetadataExtractor(
      listOf(generic, specific),
      "https://example.com/books/1",
    )

    assertEquals("specific", result?.id)
  }

  @Test
  fun `同じ具体度なら更新日時が新しいURLパターンを優先する`() {
    val old = extractor(id = "old", pattern = "https://example.com/*1", updatedAt = 10L)
    val new = extractor(id = "new", pattern = "https://example.com/a*", updatedAt = 20L)

    val result = findMatchingWebLibraryMetadataExtractor(
      listOf(old, new),
      "https://example.com/a1",
    )

    assertEquals("new", result?.id)
  }

  @Test
  fun `カスタム関数の返り値からタイトルと相対サムネイルURLを取得する`() {
    val payload = JSONObject()
      .put("title", " カスタムタイトル ")
      .put("thumbnailUrl", "/covers/1.jpg")
      .toString()

    val metadata = parseCustomRenderedWebLibraryMetadata(
      finalUrl = "https://example.com/books/1",
      rawResult = JSONObject.quote(payload),
    )

    assertEquals("カスタムタイトル", metadata?.title)
    assertEquals("https://example.com/covers/1.jpg", metadata?.thumbnailUrl)
  }

  @Test
  fun `Promise完了状態からmetadataと適用結果を取得する`() {
    val payload = JSONObject()
      .put("title", "非同期タイトル")
      .put("thumbnailUrl", "/covers/async.jpg")
      .toString()
    val pollPayload = JSONObject()
      .put("pending", false)
      .put("status", "applied")
      .put("value", payload)
      .toString()

    val poll = parseCustomMetadataPromisePoll(
      finalUrl = "https://example.com/books/1",
      rawResult = JSONObject.quote(pollPayload),
    )

    assertFalse(poll?.pending ?: true)
    assertEquals(WebLibraryMetadataExtractorStatus.APPLIED, poll?.status)
    assertEquals("非同期タイトル", poll?.metadata?.title)
    assertEquals("https://example.com/covers/async.jpg", poll?.metadata?.thumbnailUrl)
  }

  @Test
  fun `Promise rejectの理由を診断結果として保持する`() {
    val pollPayload = JSONObject()
      .put("pending", false)
      .put("status", "rejected")
      .put("value", JSONObject.NULL)
      .put("message", "selector failed")
      .toString()

    val poll = parseCustomMetadataPromisePoll(
      finalUrl = "https://example.com/books/1",
      rawResult = JSONObject.quote(pollPayload),
    )

    assertEquals(WebLibraryMetadataExtractorStatus.REJECTED, poll?.status)
    assertEquals("selector failed", poll?.message)
    assertNull(poll?.metadata)
  }

  @Test
  fun `適用済みでも有効なmetadataがなければ戻り値不正として扱う`() {
    val payload = JSONObject()
      .put("thumbnailUrl", "http://cdn.example.com/cover.jpg")
      .toString()
    val pollPayload = JSONObject()
      .put("pending", false)
      .put("status", "applied")
      .put("value", payload)
      .toString()

    val poll = parseCustomMetadataPromisePoll(
      finalUrl = "https://example.com/books/1",
      rawResult = JSONObject.quote(pollPayload),
    )

    assertEquals(WebLibraryMetadataExtractorStatus.INVALID_RESULT, poll?.status)
    assertNull(poll?.metadata)
  }

  @Test
  fun `Promise待機状態はmetadata未確定として扱う`() {
    val pollPayload = JSONObject()
      .put("pending", true)
      .put("value", JSONObject.NULL)
      .toString()

    val poll = parseCustomMetadataPromisePoll(
      finalUrl = "https://example.com/books/1",
      rawResult = JSONObject.quote(pollPayload),
    )

    assertTrue(poll?.pending == true)
    assertNull(poll?.metadata)
  }

  @Test
  fun `カスタム関数がHTTPサムネイルを返しても保存対象にしない`() {
    val payload = JSONObject()
      .put("thumbnailUrl", "http://cdn.example.com/cover.jpg")
      .toString()

    val metadata = parseCustomRenderedWebLibraryMetadata(
      finalUrl = "https://example.com/books/1",
      rawResult = JSONObject.quote(payload),
    )

    assertNull(metadata)
  }

  @Test
  fun `カスタムmetadataは指定されたタイトルとサムネイルだけを上書きする`() {
    val original = webBook(
      title = "通常タイトル",
      thumbnailUrl = "https://example.com/normal.jpg",
      description = "通常説明",
      authors = listOf("通常著者"),
    )

    val result = original.applyCustomMetadata(
      WebLibraryCustomMetadata(
        title = "カスタムタイトル",
        thumbnailUrl = "https://example.com/custom.jpg",
      ),
    )

    assertEquals("カスタムタイトル", result.title)
    assertEquals("https://example.com/custom.jpg", result.thumbnailUrl)
    assertEquals("通常説明", result.description)
    assertEquals(listOf("通常著者"), result.authors)
  }

  @Test
  fun `ルール一致時は静的metadataが揃っていてもWebViewのタイトルとサムネイルを優先する`() = runBlocking {
    var renderedCalled = false
    val staticBook = webBook(
      title = "静的タイトル",
      thumbnailUrl = "https://example.com/static.jpg",
      description = "静的説明",
      authors = listOf("静的著者"),
    )
    val renderedBook = webBook(
      title = "カスタムタイトル",
      thumbnailUrl = "https://example.com/custom.jpg",
      description = "動的説明",
      authors = listOf("動的著者"),
    )

    val result = resolveWebLibraryBookMetadata(
      url = "https://example.com/books/1",
      titleHint = null,
      staticFetch = { _, _ -> staticBook },
      renderedFetch = { _, _ ->
        renderedCalled = true
        renderedBook
      },
      preferRenderedTitleAndThumbnail = true,
    )

    assertTrue(renderedCalled)
    assertEquals("カスタムタイトル", result.title)
    assertEquals("https://example.com/custom.jpg", result.thumbnailUrl)
    assertEquals("静的説明", result.description)
    assertEquals(listOf("静的著者"), result.authors)
  }

  @Test
  fun `取得ルールの診断結果をmetadata解決結果へ伝播する`() = runBlocking {
    val staticBook = webBook(
      title = "静的タイトル",
      thumbnailUrl = "https://example.com/static.jpg",
      description = "静的説明",
      authors = listOf("静的著者"),
    )
    val renderedBook = webBook(
      title = "動的タイトル",
      thumbnailUrl = "https://example.com/rendered.jpg",
      description = "動的説明",
      authors = listOf("動的著者"),
    )
    val execution = WebLibraryMetadataExtractorExecution(
      ruleId = "rule-1",
      urlPattern = "https://example.com/books/*",
      status = WebLibraryMetadataExtractorStatus.REJECTED,
      message = "selector failed",
    )

    val result = resolveWebLibraryBookMetadataWithReport(
      url = "https://example.com/books/1",
      titleHint = null,
      staticFetch = { _, _ -> staticBook },
      renderedFetch = { _, _ ->
        WebLibraryRenderedMetadataFetchResult(
          book = renderedBook,
          extractorExecution = execution,
        )
      },
      forceRendered = true,
    )

    assertEquals(execution, result.extractorExecution)
    assertEquals("動的タイトル", result.book.title)
  }

  @Test
  fun `WebView取得失敗時は静的metadataへフォールバックして理由を保持する`() = runBlocking {
    val staticBook = webBook(
      title = "静的タイトル",
      thumbnailUrl = "https://example.com/static.jpg",
      description = "静的説明",
      authors = listOf("静的著者"),
    )

    val result = resolveWebLibraryBookMetadataWithReport(
      url = "https://example.com/books/1",
      titleHint = null,
      staticFetch = { _, _ -> staticBook },
      renderedFetch = { _, _ -> throw IllegalStateException("renderer unavailable") },
      forceRendered = true,
    )

    assertEquals(staticBook, result.book)
    assertEquals("renderer unavailable", result.fallbackReason)
  }

  @Test
  fun `カスタム関数はevaluateJavascript本体の完了を塞がない非同期タスクとして開始する`() {
    val functionCode = "async ({ url }) => ({ title: url, thumbnailUrl: null });"
    val script = customMetadataStartScript(functionCode, "test-state")
    val pollScript = customMetadataPollScript("test-state")

    assertTrue(script.contains("const source = ${JSONObject.quote(functionCode.removeSuffix(";"))}"))
    assertTrue(script.contains("setTimeout(() => {"))
    assertTrue(script.contains("extractor = eval('(' + source + ')')"))
    assertTrue(script.contains("finish('invalid_function'"))
    assertTrue(script.contains("const promise = extractor({ url: location.href })"))
    assertTrue(script.indexOf("setTimeout(() => {") < script.indexOf("const promise = extractor"))
    assertTrue(script.contains("typeof promise.then !== 'function'"))
    assertTrue(script.contains("finish('non_promise_result'"))
    assertTrue(script.contains("finish('rejected'"))
    assertTrue(script.contains("finish('threw'"))
    assertTrue(script.contains("}, 0);"))
    assertTrue(pollScript.contains("if (state.pending)"))
    assertTrue(pollScript.contains("status"))
    assertTrue(pollScript.contains("delete window[stateKey]"))
  }

  @Test
  fun `カスタムPromiseのネイティブwatchdogは10秒上限に1秒の猶予を加える`() {
    assertEquals(11_000L, customMetadataNativeWatchdogDelayMillis())
  }

  private fun extractor(
    id: String,
    pattern: String,
    updatedAt: Long,
  ) = WebLibraryMetadataExtractor(
    id = id,
    urlPattern = pattern,
    functionCode = "async () => ({ title: null, thumbnailUrl: null })",
    updatedAt = updatedAt,
  )

  private fun webBook(
    title: String,
    thumbnailUrl: String?,
    description: String?,
    authors: List<String>,
  ): LibraryBook = LibraryBook(
    source = LibrarySource.WEB,
    sourceId = "https://example.com/books/1",
    title = title,
    authors = authors,
    publisher = null,
    publishedDate = null,
    description = description,
    isbn10 = null,
    isbn13 = null,
    thumbnailUrl = thumbnailUrl,
    infoUrl = "https://example.com/books/1",
  )
}
