package dev.terashima.yomitorirss.feature.library

import java.io.InputStream

interface LibrarySeriesImportSupport {
  suspend fun importSeriesMetadata(
    source: LibrarySource,
    fileName: String?,
    input: InputStream,
  )

  suspend fun clearSeriesMetadata(source: LibrarySource)
}
