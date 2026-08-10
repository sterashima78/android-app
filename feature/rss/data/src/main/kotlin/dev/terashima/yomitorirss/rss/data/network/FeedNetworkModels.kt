package dev.terashima.yomitorirss.feature.rss.data.network

internal data class ParsedArticle(
  val externalId: String?,
  val identityKey: String,
  val url: String,
  val title: String,
  val publishedAt: String,
)

internal data class ParsedFeed(
  val title: String,
  val feedUrl: String,
  val siteUrl: String?,
  val articles: List<ParsedArticle>,
)

internal data class FetchResult(
  val feed: ParsedFeed?,
  val etag: String?,
  val lastModified: String?,
  val notModified: Boolean = false,
)
