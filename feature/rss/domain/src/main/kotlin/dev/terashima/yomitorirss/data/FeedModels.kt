package dev.terashima.yomitorirss.feature.rss

data class Feed(
  val id: String,
  val title: String,
  val feedUrl: String,
  val siteUrl: String?,
  val etag: String?,
  val lastModified: String?,
  val lastFetchedAt: String?,
  val lastError: String?,
  val createdAt: String,
)

data class FeedCandidate(
  val title: String,
  val url: String,
)

data class FeedInspection(
  val directFeedUrl: String? = null,
  val candidates: List<FeedCandidate> = emptyList(),
)
