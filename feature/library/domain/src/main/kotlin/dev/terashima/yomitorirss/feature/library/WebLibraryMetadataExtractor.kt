package dev.terashima.yomitorirss.feature.library

const val DEFAULT_WEB_LIBRARY_METADATA_TIMEOUT_SECONDS = 15
const val MIN_WEB_LIBRARY_METADATA_TIMEOUT_SECONDS = 5
const val MAX_WEB_LIBRARY_METADATA_TIMEOUT_SECONDS = 120

data class WebLibraryMetadataExtractor(
  val id: String,
  val urlPattern: String,
  val functionCode: String,
  val timeoutSeconds: Int = DEFAULT_WEB_LIBRARY_METADATA_TIMEOUT_SECONDS,
  val updatedAt: Long,
)

interface WebLibraryMetadataExtractorRepository {
  fun list(): List<WebLibraryMetadataExtractor>

  fun save(
    id: String? = null,
    urlPattern: String,
    functionCode: String,
    timeoutSeconds: Int = DEFAULT_WEB_LIBRARY_METADATA_TIMEOUT_SECONDS,
  ): WebLibraryMetadataExtractor

  fun delete(id: String)
}
