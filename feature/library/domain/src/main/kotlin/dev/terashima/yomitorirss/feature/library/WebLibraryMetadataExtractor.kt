package dev.terashima.yomitorirss.feature.library

data class WebLibraryMetadataExtractor(
  val id: String,
  val urlPattern: String,
  val functionCode: String,
  val updatedAt: Long,
)

interface WebLibraryMetadataExtractorRepository {
  fun list(): List<WebLibraryMetadataExtractor>

  fun save(
    id: String? = null,
    urlPattern: String,
    functionCode: String,
  ): WebLibraryMetadataExtractor

  fun delete(id: String)
}
