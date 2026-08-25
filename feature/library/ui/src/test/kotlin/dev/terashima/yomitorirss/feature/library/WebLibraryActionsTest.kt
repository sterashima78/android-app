package dev.terashima.yomitorirss.feature.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebLibraryActionsTest {
  @Test
  fun `取得ルール適用成功時はルールが実際に返したタイトルとサムネイルを表示する`() {
    val book = webBook(
      title = "最終タイトル",
      thumbnailUrl = "https://cdn.example.com/covers/final.jpg",
    )
    val result = WebLibraryMetadataRefreshResult(
      book = book,
      changedFields = emptySet(),
      extractorExecution = WebLibraryMetadataExtractorExecution(
        ruleId = "rule-1",
        urlPattern = "https://example.com/books/*",
        status = WebLibraryMetadataExtractorStatus.APPLIED,
        extractedTitle = "カスタムタイトル",
        extractedThumbnailUrl = "https://cdn.example.com/covers/custom.jpg",
      ),
    )

    val state = webLibraryRefreshSuccessUiState(
      sourceId = book.sourceId,
      title = book.title,
      result = result,
    )

    assertEquals(WebLibraryRefreshItemStatus.UNCHANGED, state.status)
    assertTrue(state.detail.orEmpty().contains("カスタム値取得成功"))
    assertTrue(state.detail.orEmpty().contains("タイトル「カスタムタイトル」"))
    assertTrue(state.detail.orEmpty().contains("サムネイル https://cdn.example.com/covers/custom.jpg"))
    assertFalse(state.detail.orEmpty().contains("covers/final.jpg"))
  }

  @Test
  fun `取得ルールがサムネイルを返していない場合は取得なしと表示する`() {
    val book = webBook(
      title = "最終タイトル",
      thumbnailUrl = "https://cdn.example.com/covers/standard.jpg",
    )
    val result = WebLibraryMetadataRefreshResult(
      book = book,
      changedFields = setOf(WebLibraryMetadataField.TITLE),
      extractorExecution = WebLibraryMetadataExtractorExecution(
        ruleId = "rule-1",
        urlPattern = "https://example.com/books/*",
        status = WebLibraryMetadataExtractorStatus.APPLIED,
        extractedTitle = "タイトルのみ",
        extractedThumbnailUrl = null,
      ),
    )

    val state = webLibraryRefreshSuccessUiState(
      sourceId = book.sourceId,
      title = book.title,
      result = result,
    )

    assertEquals(WebLibraryRefreshItemStatus.UPDATED, state.status)
    assertTrue(state.detail.orEmpty().contains("タイトル「タイトルのみ」・サムネイルなし"))
    assertFalse(state.detail.orEmpty().contains("covers/standard.jpg"))
  }

  private fun webBook(
    title: String,
    thumbnailUrl: String?,
  ): LibraryBook = LibraryBook(
    source = LibrarySource.WEB,
    sourceId = "https://example.com/books/1",
    title = title,
    authors = emptyList(),
    publisher = null,
    publishedDate = null,
    description = null,
    isbn10 = null,
    isbn13 = null,
    thumbnailUrl = thumbnailUrl,
    infoUrl = "https://example.com/books/1",
  )
}
