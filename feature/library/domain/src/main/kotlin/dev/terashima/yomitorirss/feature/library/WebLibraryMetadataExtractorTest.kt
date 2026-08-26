package dev.terashima.yomitorirss.feature.library

data class WebLibraryMetadataExtractorTestResult(
  val book: LibraryBook,
  val extractorExecution: WebLibraryMetadataExtractorExecution? = null,
)

fun interface WebLibraryMetadataExtractorTester {
  suspend fun test(
    url: String,
    urlPattern: String,
    functionCode: String,
    timeoutSeconds: Int,
  ): WebLibraryMetadataExtractorTestResult
}
