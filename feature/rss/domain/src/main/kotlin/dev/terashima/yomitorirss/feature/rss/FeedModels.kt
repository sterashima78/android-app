package dev.terashima.yomitorirss.feature.rss

import dev.terashima.yomitorirss.feature.article.ContentType

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
  val folderId: String? = null,
  val contentTypeOverride: ContentType? = null,
)

data class FeedFolder(
  val id: String,
  val name: String,
  val normalizedName: String,
  val createdAt: String,
  val contentTypeOverride: ContentType? = null,
)

fun Feed.effectiveContentType(folder: FeedFolder?): ContentType =
  contentTypeOverride ?: folder?.contentTypeOverride ?: ContentType.ARTICLE

fun FeedFolder.effectiveContentType(): ContentType = contentTypeOverride ?: ContentType.ARTICLE

data class FeedCandidate(
  val title: String,
  val url: String,
)

data class FeedInspection(
  val directFeedUrl: String? = null,
  val candidates: List<FeedCandidate> = emptyList(),
)
