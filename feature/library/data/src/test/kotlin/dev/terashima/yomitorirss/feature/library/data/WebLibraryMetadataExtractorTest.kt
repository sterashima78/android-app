package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibrarySource
import dev.terashima.yomitorirss.feature.library.WebLibraryMetadataExtractor
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
    val old = extractor(id = "old", pattern = "https://example.com/book/*", updatedAt = 10L)
    val new = extractor(id = "new", pattern = "https://example.com/item/*", updatedAt = 20L)

    val result = findMatchingWebLibraryMetadataExtractor(
      listOf(old, new),
      "https://example.com/item/1",
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
  fun `関数コードは同期関数としてURL引数を受け取るスクリプトへ埋め込む`() {
    val script = customMetadataScript("({ url }) => ({ title: url, thumbnailUrl: null });")

    assertTrue(script.contains("const extractor = (({ url }) => ({ title: url, thumbnailUrl: null }))"))
    assertTrue(script.contains("extractor({ url: location.href })"))
  }

  private fun extractor(
    id: String,
    pattern: String,
    updatedAt: Long,
  ) = WebLibraryMetadataExtractor(
    id = id,
    urlPattern = pattern,
    functionCode = "() => ({ title: null, thumbnailUrl: null })",
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
