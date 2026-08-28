package dev.terashima.yomitorirss.feature.web

interface LanWebContentGateway {
  suspend fun listUnreadArticles(): List<LanWebArticleItem>

  suspend fun listSavedArticles(): List<LanWebArticleItem>

  suspend fun listReadLaterArticles(): List<LanWebArticleItem>

  suspend fun listFeeds(): List<LanWebFeedItem>
}

interface LanWebContentGatewayProvider {
  val lanWebContentGateway: LanWebContentGateway
}

data class LanWebArticleItem(
  val title: String,
  val url: String,
  val sourceTitle: String,
  val publishedAt: String,
  val sourceKind: LanWebSourceKind,
  val tagNames: List<String> = emptyList(),
)

data class LanWebFeedItem(
  val title: String,
  val feedUrl: String,
  val siteUrl: String?,
  val sourceKind: LanWebSourceKind,
)

enum class LanWebSourceKind {
  RSS,
  REDDIT,
}
