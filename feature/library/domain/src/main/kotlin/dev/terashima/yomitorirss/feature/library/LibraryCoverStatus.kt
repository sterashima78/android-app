package dev.terashima.yomitorirss.feature.library

enum class LibraryCoverAcquisitionState {
  SOURCE_PROVIDED,
  ACQUIRED,
  WAITING,
  NOT_FOUND,
  AMBIGUOUS,
}

data class LibraryCoverAcquisitionItem(
  val source: LibrarySource,
  val sourceId: String,
  val title: String,
  val state: LibraryCoverAcquisitionState,
  val provider: String?,
  val lastAttemptAtEpochMillis: Long?,
  val diagnosticTrace: String? = null,
)

data class LibraryCoverAcquisitionSnapshot(
  val items: List<LibraryCoverAcquisitionItem> = emptyList(),
  val kindleCoverEnrichmentEnabled: Boolean = false,
) {
  fun count(state: LibraryCoverAcquisitionState): Int = items.count { it.state == state }
}

enum class LibraryCoverWorkState {
  DISABLED,
  IDLE,
  WAITING,
  RUNNING,
  RETRY_WAITING,
  FAILED,
}
