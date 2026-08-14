package dev.terashima.yomitorirss.feature.library

import kotlinx.coroutines.flow.Flow

enum class LibraryCoverAcquisitionState {
  SOURCE_PROVIDED,
  ACQUIRED,
  WAITING,
  NOT_FOUND,
  AMBIGUOUS,
  ERROR,
}

data class LibraryCoverAcquisitionItem(
  val source: LibrarySource,
  val sourceId: String,
  val title: String,
  val state: LibraryCoverAcquisitionState,
  val provider: String?,
  val lastAttemptAtEpochMillis: Long?,
  val diagnosticTrace: String? = null,
  val retryCount: Int = 0,
  val nextAttemptAtEpochMillis: Long? = null,
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

data class LibraryCoverWorkSnapshot(
  val states: Map<LibrarySource, LibraryCoverWorkState> = emptyMap(),
  val finishedWorkCount: Int = 0,
)

interface LibraryCoverEnrichmentCoordinator {
  val workSnapshots: Flow<LibraryCoverWorkSnapshot>

  fun sync(kindleEnabled: Boolean)

  suspend fun snapshot(): LibraryCoverAcquisitionSnapshot

  suspend fun retryUnresolved(kindleEnabled: Boolean)

  fun cancel()
}
