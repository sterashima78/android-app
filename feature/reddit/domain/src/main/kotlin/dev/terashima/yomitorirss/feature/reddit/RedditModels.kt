package dev.terashima.yomitorirss.feature.reddit

import dev.terashima.yomitorirss.feature.article.Article
import kotlinx.coroutines.flow.StateFlow

enum class RedditSubscriptionKind {
  COMMUNITY,
  THREAD,
}

data class RedditSubscription(
  val id: String,
  val title: String,
  val feedUrl: String,
  val kind: RedditSubscriptionKind,
  val lastFetchedAt: String?,
  val lastError: String?,
)

data class RedditRefreshResult(
  val total: Int,
  val failures: Int,
)

interface RedditRepository {
  val changes: StateFlow<Long>
  suspend fun listSubscriptions(): List<RedditSubscription>
  suspend fun addCommunity(input: String)
  suspend fun subscribeThread(articleUrl: String)
  suspend fun unsubscribeThread(articleUrl: String)
  suspend fun deleteSubscription(subscriptionId: String)
  suspend fun refreshAll(onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> }): RedditRefreshResult
}

fun Article.isRedditArticle(): Boolean = isRedditFeedUrl(sourceFeedUrl)
