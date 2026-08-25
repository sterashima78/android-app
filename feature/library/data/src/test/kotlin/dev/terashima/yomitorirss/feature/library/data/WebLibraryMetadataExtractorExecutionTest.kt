package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibrarySource
import dev.terashima.yomitorirss.feature.library.WebLibraryMetadataExtractor
import dev.terashima.yomitorirss.feature.library.WebLibraryMetadataExtractorExecution
import dev.terashima.yomitorirss.feature.library.WebLibraryMetadataExtractorStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class WebLibraryMetadataExtractorExecutionTest {
  @Test
  fun `取得ルール診断にカスタム関数が返した値を保持する`() {
    val extractor = WebLibraryMetadataExtractor(
      id = "rule-1",
      urlPattern = "https://example.com/books/*",
      functionCode = "async () => ({ title: 'title', thumbnailUrl: null })",
      updatedAt = 1L,
    )

    val execution = createWebLibraryMetadataExtractorExecution(
      extractor = extractor,
      status = WebLibraryMetadataExtractorStatus.APPLIED,
      metadata = WebLibraryCustomMetadata(
        title = "カスタムタイトル",
        thumbnailUrl = "https://cdn.example.com/covers/custom.jpg",
      ),
    )

    assertEquals("カスタムタイトル", execution.extractedTitle)
    assertEquals("https://cdn.example.com/covers/custom.jpg", execution.extractedThumbnailUrl)
  }

  @Test
  fun `WebView全体失敗時も取得済みのカスタム値を静的fallback結果へ伝播する`() = runBlocking {
    val staticBook = webBook(
      title = "静的タイトル",
      thumbnailUrl = "https://cdn.example.com/covers/static.jpg",
    )
    val execution = WebLibraryMetadataExtractorExecution(
      ruleId = "rule-1",
      urlPattern = "https://example.com/books/*",
      status = WebLibraryMetadataExtractorStatus.APPLIED,
      extractedTitle = "カスタムタイトル",
      extractedThumbnailUrl = "https://cdn.example.com/covers/custom.jpg",
    )

    val result = resolveWebLibraryBookMetadataWithReport(
      url = staticBook.sourceId,
      titleHint = null,
      staticFetch = { _, _ -> staticBook },
      renderedFetch = { _, _ ->
        throw WebLibraryRenderedMetadataException(
          message = "WebView metadata 取得が 15 秒以内に完了しませんでした",
          extractorExecution = execution,
        )
      },
      forceRendered = true,
    )

    assertEquals(staticBook, result.book)
    assertEquals(execution, result.extractorExecution)
    assertEquals("WebView metadata 取得が 15 秒以内に完了しませんでした", result.fallbackReason)
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
