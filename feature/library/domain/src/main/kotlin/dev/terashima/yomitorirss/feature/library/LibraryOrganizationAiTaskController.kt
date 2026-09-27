package dev.terashima.yomitorirss.feature.library

enum class LibraryOrganizationAiTaskState {
  QUEUED,
  RUNNING,
  SUCCEEDED,
  FAILED,
  CANCELLED,
}

data class LibraryOrganizationAiTaskSnapshot(
  val state: LibraryOrganizationAiTaskState,
  val suggestion: LibraryOrganizationSuggestion? = null,
  val seriesResult: LibrarySeriesReorganizationResult? = null,
  val error: String? = null,
)

interface LibraryOrganizationAiTaskController {
  suspend fun enqueueSuggestion(book: LibraryBook): String
  suspend fun enqueueSeriesReorganization(book: LibraryBook): String
  suspend fun snapshot(requestId: String): LibraryOrganizationAiTaskSnapshot
}
