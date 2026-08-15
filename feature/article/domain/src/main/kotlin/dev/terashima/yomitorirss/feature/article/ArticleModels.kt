package dev.terashima.yomitorirss.feature.article

data class Article(
  val id: String,
  val feedId: String?,
  val externalId: String?,
  val identityKey: String,
  val url: String,
  val title: String,
  val publishedAt: String,
  val fetchedAt: String,
  val readAt: String?,
  val sourceTitle: String,
  val sourceFeedUrl: String,
)
