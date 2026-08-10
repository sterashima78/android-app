package dev.terashima.yomitorirss.feature.reddit.data

import dev.terashima.yomitorirss.feature.reddit.RedditRefreshResult
import dev.terashima.yomitorirss.feature.reddit.RedditRepository
import dev.terashima.yomitorirss.feature.reddit.RedditSubscription
import dev.terashima.yomitorirss.feature.reddit.isRedditFeedUrl
import dev.terashima.yomitorirss.feature.reddit.redditCommunityFeedUrl
import dev.terashima.yomitorirss.feature.reddit.redditSubscriptionKind
import dev.terashima.yomitorirss.feature.reddit.redditThreadFeedUrl
import dev.terashima.yomitorirss.feature.reddit.redditThreadId
import dev.terashima.yomitorirss.feature.rss.FeedRepository
import kotlinx.coroutines.flow.StateFlow

class DefaultRedditRepository(
  private val feeds: FeedRepository,
) : RedditRepository {
  override val changes: StateFlow<Long> = feeds.changes

  override suspend fun listSubscriptions(): List<RedditSubscription> = feeds.listFeeds()
    .filter { isRedditFeedUrl(it.feedUrl) }
    .mapNotNull { feed ->
      val kind = redditSubscriptionKind(feed.feedUrl) ?: return@mapNotNull null
      RedditSubscription(
        id = feed.id,
        title = feed.title,
        feedUrl = feed.feedUrl,
        kind = kind,
        lastFetchedAt = feed.lastFetchedAt,
        lastError = feed.lastError,
      )
    }

  override suspend fun addCommunity(input: String) {
    val feedUrl = redditCommunityFeedUrl(input)
      ?: error("RedditコミュニティのURLまたは r/コミュニティ名を入力してください")
    if (feeds.listFeeds().any { sameUrl(it.feedUrl, feedUrl) }) {
      error("このコミュニティはすでに購読中です")
    }
    feeds.addFeed(feedUrl)
  }

  override suspend fun subscribeThread(articleUrl: String) {
    val threadId = redditThreadId(articleUrl)
      ?: error("RedditスレッドのURLを認識できませんでした")
    val feedUrl = redditThreadFeedUrl(articleUrl)
      ?: error("RedditスレッドのURLを認識できませんでした")
    if (feeds.listFeeds().any { redditThreadId(it.feedUrl) == threadId }) {
      error("このスレッドはすでに購読中です")
    }
    feeds.addFeed(feedUrl, markExistingArticlesRead = true)
  }

  override suspend fun unsubscribeThread(articleUrl: String) {
    val threadId = redditThreadId(articleUrl)
      ?: error("RedditスレッドのURLを認識できませんでした")
    val feed = feeds.listFeeds().firstOrNull { redditThreadId(it.feedUrl) == threadId }
      ?: error("このスレッドは購読されていません")
    feeds.deleteFeed(feed.id)
  }

  override suspend fun deleteSubscription(subscriptionId: String) {
    val subscription = listSubscriptions().firstOrNull { it.id == subscriptionId }
      ?: error("Reddit購読が見つかりません")
    feeds.deleteFeed(subscription.id)
  }

  override suspend fun refreshAll(
    onProgress: (completed: Int, total: Int) -> Unit,
  ): RedditRefreshResult {
    val redditFeeds = feeds.listFeeds().filter { isRedditFeedUrl(it.feedUrl) }
    var failures = 0
    redditFeeds.forEachIndexed { index, feed ->
      runCatching { feeds.refreshFeed(feed) }
        .onFailure { failures += 1 }
      onProgress(index + 1, redditFeeds.size)
    }
    return RedditRefreshResult(total = redditFeeds.size, failures = failures)
  }
}

private fun sameUrl(left: String, right: String): Boolean =
  left.trimEnd('/').equals(right.trimEnd('/'), ignoreCase = true)
