package dev.terashima.yomitorirss.feature.widget
data class WidgetArticle(
  val id: String,
  val url: String,
  val title: String,
  val sourceTitle: String,
  val publishedAt: String,
)

interface WidgetRepository {
  fun listUnreadArticles(): List<WidgetArticle>
  suspend fun markRead(articleId: String)
  suspend fun markReadLater(articleId: String)
  suspend fun refreshFeeds()
}

interface WidgetRepositoryProvider {
  val widgetRepository: WidgetRepository
}
