package dev.terashima.yomitorirss.feature.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebLibraryActionsTest {
  @Test
  fun `取得ルール適用成功時は適用後のタイトルとサムネイルを表示する`() {
    val book = webBook(
      title = "カスタムタイトル",
      thumbnailUrl = "https://cdn.example.com/covers/1.jpg",
    )
    val result = WebLibraryMetadataRefreshResult(
      book = book,
      changedFields = emptySet(),
      extractorExecution = WebLibraryMetadataExtractorExecution(
        ruleId = "rule-1",
        urlPattern = "https://example.com/books/*",
        status = WebLibraryMetadataExtractorStatus.APPLIED,
      ),
    )

    val state = webLibraryRefreshSuccessUiState(
      sourceId = book.sourceId,
      title = book.title,
      result = result,
    )

    assertEquals(WebLibraryRefreshItemStatus.UNCHANGED, state.status)
    assertTrue(state.detail.orEmpty().contains("カスタム値取得成功"))
    assertTrue(state.detail.orEmpty().contains("適用後タイトル「カスタムタイトル」"))
    assertTrue(state.detail.orEmpty().contains("適用後サムネイル https://cdn.example.com/covers/1.jpg"))
  }

  @Test
  fun `取得ルール適用成功時はサムネイルがないことも表示する`() {
    val book = webBook(
      title = "タイトルのみ",
      thumbnailUrl = null,
    )
    val result = WebLibraryMetadataRefreshResult(
      book = book,
      changedFields = setOf(WebLibraryMetadataField.TITLE),
      extractorExecution = WebLibraryMetadataExtractorExecution(
        ruleId = "rule-1",
        urlPattern = "https://example.com/books/*",
        status = WebLibraryMetadataExtractorStatus.APPLIED,
      ),
    )

    val state = webLibraryRefreshSuccessUiState(
      sourceId = book.sourceId,
      title = book.title,
      result = result,
    )

    assertEquals(WebLibraryRefreshItemStatus.UPDATED, state.status)
    assertTrue(state.detail.orEmpty().contains("適用後サムネイル なし"))
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
