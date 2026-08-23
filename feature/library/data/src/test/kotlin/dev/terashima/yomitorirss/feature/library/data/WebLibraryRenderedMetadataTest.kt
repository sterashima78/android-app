package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibrarySource
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebLibraryRenderedMetadataTest {
  @Test
  fun `静的metadataが揃っていればWebView取得を行わない`() = runBlocking {
    var renderedCalled = false
    val staticBook = webBook(
      title = "静的タイトル",
      thumbnailUrl = "https://example.com/static.jpg",
    )

    val result = resolveWebLibraryBookMetadata(
      url = "https://example.com/book",
      titleHint = null,
      staticFetch = { _, _ -> staticBook },
      renderedFetch = { _, _ ->
        renderedCalled = true
        webBook(title = "動的タイトル", thumbnailUrl = "https://example.com/dynamic.jpg")
      },
    )

    assertEquals(staticBook, result)
    assertFalse(renderedCalled)
  }

  @Test
  fun `静的metadataのサムネイル欠落をWebView結果で補完する`() = runBlocking {
    val staticBook = webBook(
      title = "静的タイトル",
      thumbnailUrl = null,
      description = null,
      authors = emptyList(),
    )
    val renderedBook = webBook(
      sourceId = "https://example.com/rendered",
      title = "動的タイトル",
      thumbnailUrl = "https://example.com/dynamic.jpg",
      description = "動的説明",
      authors = listOf("著者"),
    )

    val result = resolveWebLibraryBookMetadata(
      url = "https://example.com/book",
      titleHint = null,
      staticFetch = { _, _ -> staticBook },
      renderedFetch = { _, _ -> renderedBook },
    )

    assertEquals("静的タイトル", result.title)
    assertEquals(staticBook.sourceId, result.sourceId)
    assertEquals("https://example.com/dynamic.jpg", result.thumbnailUrl)
    assertEquals("動的説明", result.description)
    assertEquals(listOf("著者"), result.authors)
  }

  @Test
  fun `host名だけの静的タイトルはWebView結果で置き換える`() = runBlocking {
    val staticBook = webBook(
      title = "example.com",
      thumbnailUrl = null,
    )
    val renderedBook = webBook(
      title = "JavaScript 後のタイトル",
      thumbnailUrl = "https://example.com/dynamic.jpg",
    )

    val result = resolveWebLibraryBookMetadata(
      url = "https://example.com/book",
      titleHint = null,
      staticFetch = { _, _ -> staticBook },
      renderedFetch = { _, _ -> renderedBook },
    )

    assertEquals("JavaScript 後のタイトル", result.title)
  }

  @Test
  fun `HTTP取得が失敗してもWebView取得に成功すれば追加できる`() = runBlocking {
    val renderedBook = webBook(
      title = "動的タイトル",
      thumbnailUrl = "https://example.com/dynamic.jpg",
    )

    val result = resolveWebLibraryBookMetadata(
      url = "https://example.com/book",
      titleHint = null,
      staticFetch = { _, _ -> error("HTTP blocked") },
      renderedFetch = { _, _ -> renderedBook },
    )

    assertEquals(renderedBook, result)
  }

  @Test
  fun `WebView取得が失敗した場合は取得済みの静的metadataを保持する`() = runBlocking {
    val staticBook = webBook(
      title = "静的タイトル",
      thumbnailUrl = null,
    )

    val result = resolveWebLibraryBookMetadata(
      url = "https://example.com/book",
      titleHint = null,
      staticFetch = { _, _ -> staticBook },
      renderedFetch = { _, _ -> error("WebView failed") },
    )

    assertEquals(staticBook, result)
  }

  @Test
  fun `WebView評価結果から相対サムネイルURLをHTTPSとして解決する`() {
    val payload = JSONObject()
      .put("url", "https://example.com/books/1")
      .put("title", "動的タイトル")
      .put("description", "説明")
      .put("image", "/images/cover.jpg")
      .put("author", "著者A、著者B")
      .toString()

    val book = parseRenderedWebLibraryBook(
      requestedUrl = "https://example.com/start",
      rawResult = JSONObject.quote(payload),
    )

    assertEquals("https://example.com/books/1", book.sourceId)
    assertEquals("動的タイトル", book.title)
    assertEquals("https://example.com/images/cover.jpg", book.thumbnailUrl)
    assertEquals(listOf("著者A", "著者B"), book.authors)
    assertEquals("説明", book.description)
  }

  @Test
  fun `WebView評価結果のHTTPサムネイルURLは保存しない`() {
    val payload = JSONObject()
      .put("url", "https://example.com/books/1")
      .put("title", "動的タイトル")
      .put("image", "http://cdn.example.com/cover.jpg")
      .toString()

    val book = parseRenderedWebLibraryBook(
      requestedUrl = "https://example.com/start",
      rawResult = JSONObject.quote(payload),
    )

    assertNull(book.thumbnailUrl)
  }

  @Test
  fun `HTTPページではWebViewフォールバックを起動しない`() = runBlocking {
    var renderedCalled = false
    val staticBook = webBook(
      sourceId = "http://example.com/book",
      title = "example.com",
      thumbnailUrl = null,
    )

    val result = resolveWebLibraryBookMetadata(
      url = "http://example.com/book",
      titleHint = null,
      staticFetch = { _, _ -> staticBook },
      renderedFetch = { _, _ ->
        renderedCalled = true
        webBook(title = "動的タイトル", thumbnailUrl = null)
      },
    )

    assertEquals(staticBook, result)
    assertFalse(renderedCalled)
  }

  @Test
  fun `不足metadataの判定はサムネイル欠落またはhostタイトルを対象にする`() {
    assertTrue(webBook(title = "タイトル", thumbnailUrl = null).needsRenderedWebMetadata())
    assertTrue(
      webBook(title = "example.com", thumbnailUrl = "https://example.com/cover.jpg")
        .needsRenderedWebMetadata(),
    )
    assertFalse(
      webBook(title = "タイトル", thumbnailUrl = "https://example.com/cover.jpg")
        .needsRenderedWebMetadata(),
    )
  }

  private fun webBook(
    sourceId: String = "https://example.com/book",
    title: String,
    thumbnailUrl: String?,
    description: String? = "静的説明",
    authors: List<String> = listOf("静的著者"),
  ): LibraryBook = LibraryBook(
    source = LibrarySource.WEB,
    sourceId = sourceId,
    title = title,
    authors = authors,
    publisher = null,
    publishedDate = null,
    description = description,
    isbn10 = null,
    isbn13 = null,
    thumbnailUrl = thumbnailUrl,
    infoUrl = sourceId,
  )
}
