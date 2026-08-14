package dev.terashima.yomitorirss.feature.library

interface LibrarySeriesImportSupport {
  suspend fun importSeriesMetadataJson(
    source: LibrarySource,
    json: String,
  )

  suspend fun clearSeriesMetadata(source: LibrarySource)
}
