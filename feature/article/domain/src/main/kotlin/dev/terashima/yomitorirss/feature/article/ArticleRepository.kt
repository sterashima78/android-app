package dev.terashima.yomitorirss.feature.article

import kotlinx.coroutines.flow.StateFlow

interface ArticleRepository {
  val changes: StateFlow<Long>
  suspend fun cleanupExpiredArticles()
  suspend fun listUnreadArticles(): List<Article>
  suspend fun listHistoryArticles(): List<Article>
  suspend fun markArticleRead(articleId: String)
  suspend fun markArticleUnread(articleId: String)
  suspend fun markAllUnreadAsRead(): Int
  suspend fun setArticleContentType(articleId: String, contentType: ContentType?)
}
