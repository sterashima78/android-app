package dev.terashima.yomitorirss.feature.library

enum class LibrarySource(val label: String) {
  GOOGLE_PLAY_BOOKS("Google Play Books"),
  KINDLE("Kindle"),
  AUDIBLE("Audible"),
}

data class LibrarySeries(
  val name: String,
  val position: Int?,
)

data class LibraryBook(
  val source: LibrarySource,
  val sourceId: String,
  val title: String,
  val authors: List<String>,
  val publisher: String?,
  val publishedDate: String?,
  val description: String?,
  val isbn10: String?,
  val isbn13: String?,
  val thumbnailUrl: String?,
  val infoUrl: String?,
  val series: LibrarySeries? = null,
  val automaticSeriesExcluded: Boolean = false,
  val narrators: List<String> = emptyList(),
  val duration: String? = null,
)

data class LibrarySourceState(
  val source: LibrarySource,
  val accountLabel: String?,
  val lastSyncedAtEpochMillis: Long?,
)

data class LibrarySnapshot(
  val books: List<LibraryBook>,
  val hiddenBooks: List<LibraryBook>,
  val sourceStates: Map<LibrarySource, LibrarySourceState>,
  val kindleCoverEnrichmentEnabled: Boolean = false,
)

data class LibrarySyncResult(
  val importedCount: Int,
  val syncedAtEpochMillis: Long,
)
