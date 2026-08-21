package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.library.LibraryRepository
import dev.terashima.yomitorirss.feature.library.LibrarySeriesImportSupport
import dev.terashima.yomitorirss.feature.library.LibrarySnapshot

class SmbMetadataAwareLibraryRepository private constructor(
  private val database: DatabaseConnection,
  private val delegate: SeriesAwareLibraryRepository,
) : LibraryRepository by delegate, LibrarySeriesImportSupport by delegate {
  constructor(database: DatabaseConnection) : this(
    database = database,
    delegate = SeriesAwareLibraryRepository(database),
  )

  override suspend fun snapshot(): LibrarySnapshot = delegate.snapshot().let { snapshot ->
    snapshot.copy(
      books = applyConfirmedSmbMetadata(database, snapshot.books),
      hiddenBooks = applyConfirmedSmbMetadata(database, snapshot.hiddenBooks),
    )
  }
}
