package dev.terashima.yomitorirss.feature.library.data

import android.app.Activity
import dev.terashima.yomitorirss.feature.library.WebLibraryMetadataExtractor
import dev.terashima.yomitorirss.feature.library.WebLibraryMetadataExtractorRepository
import dev.terashima.yomitorirss.feature.library.WebLibraryMetadataExtractorTestResult
import dev.terashima.yomitorirss.feature.library.WebLibraryMetadataExtractorTester

class AndroidWebViewLibraryMetadataExtractorTester(
  private val activityProvider: () -> Activity?,
) : WebLibraryMetadataExtractorTester {
  override suspend fun test(
    url: String,
    urlPattern: String,
    functionCode: String,
    timeoutSeconds: Int,
  ): WebLibraryMetadataExtractorTestResult {
    val draft = WebLibraryMetadataExtractor(
      id = PREVIEW_RULE_ID,
      urlPattern = urlPattern,
      functionCode = functionCode,
      timeoutSeconds = timeoutSeconds,
      updatedAt = 0L,
    )
    val client = AndroidWebViewLibraryMetadataClient(
      activityProvider = activityProvider,
      extractorRepository = PreviewWebLibraryMetadataExtractorRepository(draft),
    )
    val result = client.fetchWithReport(url)
    return WebLibraryMetadataExtractorTestResult(
      book = result.book,
      extractorExecution = result.extractorExecution,
    )
  }
}

private class PreviewWebLibraryMetadataExtractorRepository(
  private val extractor: WebLibraryMetadataExtractor,
) : WebLibraryMetadataExtractorRepository {
  override fun list(): List<WebLibraryMetadataExtractor> = listOf(extractor)

  override fun save(
    id: String?,
    urlPattern: String,
    functionCode: String,
    timeoutSeconds: Int,
  ): WebLibraryMetadataExtractor = error("preview repository is read-only")

  override fun delete(id: String): Unit = error("preview repository is read-only")
}

private const val PREVIEW_RULE_ID = "preview"
