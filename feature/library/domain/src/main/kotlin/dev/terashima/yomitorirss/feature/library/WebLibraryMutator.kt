package dev.terashima.yomitorirss.feature.library

enum class WebLibraryMetadataField {
  TITLE,
  THUMBNAIL,
  DESCRIPTION,
  AUTHORS,
}

enum class WebLibraryMetadataExtractorStatus {
  MATCHED,
  RUNNING,
  APPLIED,
  EMPTY_RESULT,
  INVALID_FUNCTION,
  NON_PROMISE_RESULT,
  REJECTED,
  THREW,
  TIMED_OUT,
  INVALID_STATE,
  INVALID_RESULT,
}

data class WebLibraryMetadataExtractorExecution(
  val ruleId: String,
  val urlPattern: String,
  val status: WebLibraryMetadataExtractorStatus,
  val message: String? = null,
  val extractedTitle: String? = null,
  val extractedThumbnailUrl: String? = null,
)

data class WebLibraryMetadataRefreshResult(
  val book: LibraryBook,
  val changedFields: Set<WebLibraryMetadataField>,
  val extractorExecution: WebLibraryMetadataExtractorExecution? = null,
  val fallbackReason: String? = null,
)

fun changedWebLibraryMetadataFields(
  before: LibraryBook,
  after: LibraryBook,
): Set<WebLibraryMetadataField> = buildSet {
  if (before.title != after.title) add(WebLibraryMetadataField.TITLE)
  if (before.thumbnailUrl != after.thumbnailUrl) add(WebLibraryMetadataField.THUMBNAIL)
  if (before.description != after.description) add(WebLibraryMetadataField.DESCRIPTION)
  if (before.authors != after.authors) add(WebLibraryMetadataField.AUTHORS)
}

interface WebLibraryMutator {
  suspend fun addWebBook(url: String, titleHint: String? = null): LibraryBook

  suspend fun refreshWebBook(book: LibraryBook): LibraryBook = addWebBook(book.infoUrl ?: book.sourceId, null)

  suspend fun refreshWebBookWithReport(book: LibraryBook): WebLibraryMetadataRefreshResult {
    val refreshed = refreshWebBook(book)
    return WebLibraryMetadataRefreshResult(
      book = refreshed,
      changedFields = changedWebLibraryMetadataFields(book, refreshed),
    )
  }

  suspend fun removeWebBook(book: LibraryBook)
}
