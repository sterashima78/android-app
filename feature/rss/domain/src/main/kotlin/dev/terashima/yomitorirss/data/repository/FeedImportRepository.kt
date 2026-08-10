package dev.terashima.yomitorirss.feature.rss

data class FeedOpmlImportResult(
  val added: Int,
  val duplicates: Int,
  val skipped: Int,
)

interface FeedImportRepository {
  suspend fun importFeedOpml(documentUri: String): FeedOpmlImportResult
}
