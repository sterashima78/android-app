package dev.terashima.yomitorirss.feature.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebLibraryRefreshResultTest {
  @Test
  fun `取得ルール適用と更新項目を成功結果へ表示する`() {
    val result = WebLibraryMetadataRefreshResult(
      book = webBook(title = "更新後", thumbnailUrl = "https://example.com/new.jpg"),
      changedFields = setOf(WebLibraryMetadataField.TITLE, WebLibraryMetadataField.THUMBNAIL),
      extractorExecution = WebLibraryMetadataExtractorExecution(
        ruleId = "rule-1",
        urlPattern = "https://example.com/books/*",
        status = WebLibraryMetadataExtractorStatus.APPLIED,
      ),
    )

    val ui = webLibraryRefreshSuccessUiState(
      sourceId = result.book.sourceId,
      title = result.book.title,
      result = result,
    )

    assertEquals(WebLibraryRefreshItemStatus.UPDATED, ui.status)
    assertTrue(ui.detail.orEmpty().contains("https://example.com/books/*"))
    assertTrue(ui.detail.orEmpty().contains("タイトル・サムネイル"))
  }

  @Test
  fun `取得ルール失敗は標準取得成功でも要確認として理由を表示する`() {
    val result = WebLibraryMetadataRefreshResult(
      book = webBook(),
      changedFields = emptySet(),
      extractorExecution = WebLibraryMetadataExtractorExecution(
        ruleId = "rule-1",
        urlPattern = "https://example.com/books/*",
        status = WebLibraryMetadataExtractorStatus.REJECTED,
        message = "selector failed",
      ),
    )

    val ui = webLibraryRefreshSuccessUiState(
      sourceId = result.book.sourceId,
      title = result.book.title,
      result = result,
    )

    assertEquals(WebLibraryRefreshItemStatus.WARNING, ui.status)
    assertTrue(ui.detail.orEmpty().contains("Promise が reject"))
    assertTrue(ui.detail.orEmpty().contains("selector failed"))
    assertTrue(ui.detail.orEmpty().contains("標準取得を使用"))
    assertTrue(ui.detail.orEmpty().contains("metadata の変更なし"))
  }

  @Test
  fun `WebView全体の失敗で静的metadataへ戻った場合は要確認として表示する`() {
    val result = WebLibraryMetadataRefreshResult(
      book = webBook(),
      changedFields = emptySet(),
      fallbackReason = "renderer unavailable",
    )

    val ui = webLibraryRefreshSuccessUiState(
      sourceId = result.book.sourceId,
      title = result.book.title,
      result = result,
    )

    assertEquals(WebLibraryRefreshItemStatus.WARNING, ui.status)
    assertTrue(ui.detail.orEmpty().contains("renderer unavailable"))
    assertTrue(ui.detail.orEmpty().contains("静的 metadata を使用"))
  }

  @Test
  fun `取得成功で値が変わらなければ変更なしとして表示する`() {
    val result = WebLibraryMetadataRefreshResult(
      book = webBook(),
      changedFields = emptySet(),
    )

    val ui = webLibraryRefreshSuccessUiState(
      sourceId = result.book.sourceId,
      title = result.book.title,
      result = result,
    )

    assertEquals(WebLibraryRefreshItemStatus.UNCHANGED, ui.status)
    assertTrue(ui.detail.orEmpty().contains("metadata の変更なし"))
  }

  private fun webBook(
    title: String = "タイトル",
    thumbnailUrl: String? = "https://example.com/cover.jpg",
  ) = LibraryBook(
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
