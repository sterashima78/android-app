package dev.terashima.yomitorirss.feature.rss

import kotlinx.coroutines.flow.StateFlow

interface FeedRepository {
  val changes: StateFlow<Long>
  suspend fun listFeeds(): List<Feed>
  suspend fun inspect(input: String): FeedInspection
  suspend fun addFeed(url: String, markExistingArticlesRead: Boolean = false)
  suspend fun deleteFeed(feedId: String)
  suspend fun refreshFeed(feed: Feed)
}
