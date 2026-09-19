package dev.terashima.yomitorirss.feature.reddit.data

import dev.terashima.yomitorirss.feature.article.ContentType
import dev.terashima.yomitorirss.feature.reddit.RedditSubscriptionKind
import dev.terashima.yomitorirss.feature.rss.Feed
import dev.terashima.yomitorirss.feature.rss.FeedFolder
import dev.terashima.yomitorirss.feature.rss.FeedInspection
import dev.terashima.yomitorirss.feature.rss.FeedRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultRedditRepositoryTest {
  @Test
  fun `Reddit以外のfeedを購読一覧から除外する`() = runBlocking {
    val feeds = FakeFeedRepository(
      feeds = mutableListOf(
        feed("community", "https://www.reddit.com/r/androiddev/new/.rss"),
        feed("thread", "https://www.reddit.com/r/androiddev/comments/abc123/title/.rss"),
        feed("other", "https://example.invalid/feed.xml"),
      ),
    )
    val repository = DefaultRedditRepository(feeds)

    val subscriptions = repository.listSubscriptions()

    assertEquals(listOf("community", "thread"), subscriptions.map { it.id })
    assertEquals(
      listOf(RedditSubscriptionKind.COMMUNITY, RedditSubscriptionKind.THREAD),
      subscriptions.map { it.kind },
    )
  }

  @Test
  fun `同一communityは末尾slashと大文字小文字の差を無視して重複拒否する`() = runBlocking {
    val feeds = FakeFeedRepository(
      feeds = mutableListOf(
        feed("existing", "https://www.reddit.com/r/androiddev/new/.rss/"),
      ),
    )
    val repository = DefaultRedditRepository(feeds)

    val error = runCatching { repository.addCommunity("r/AndroidDev") }.exceptionOrNull()

    assertTrue(error is IllegalStateException)
    assertEquals(1, feeds.feeds.size)
  }

  @Test
  fun `refreshはReddit feedだけを全件処理し部分失敗を集計する`() = runBlocking {
    val feeds = FakeFeedRepository(
      feeds = mutableListOf(
        feed("ok", "https://www.reddit.com/r/androiddev/new/.rss"),
        feed("fail", "https://www.reddit.com/r/kotlin/new/.rss"),
        feed("other", "https://example.invalid/feed.xml"),
      ),
      failingRefreshIds = setOf("fail"),
    )
    val repository = DefaultRedditRepository(feeds)
    val progress = mutableListOf<Pair<Int, Int>>()

    val result = repository.refreshAll { completed, total -> progress += completed to total }

    assertEquals(2, result.total)
    assertEquals(1, result.failures)
    assertEquals(listOf("ok", "fail"), feeds.refreshedIds)
    assertEquals(listOf(1 to 2, 2 to 2), progress)
  }

  private fun feed(id: String, url: String) = Feed(
    id = id,
    title = id,
    feedUrl = url,
    siteUrl = null,
    etag = null,
    lastModified = null,
    lastFetchedAt = null,
    lastError = null,
    createdAt = "2026-01-01T00:00:00Z",
  )
}

private class FakeFeedRepository(
  val feeds: MutableList<Feed>,
  private val failingRefreshIds: Set<String> = emptySet(),
) : FeedRepository {
  override val changes: StateFlow<Long> = MutableStateFlow(0)
  val refreshedIds = mutableListOf<String>()

  override suspend fun listFeeds(): List<Feed> = feeds.toList()
  override suspend fun listFolders(): List<FeedFolder> = emptyList()
  override suspend fun inspect(input: String): FeedInspection = FeedInspection()
  override suspend fun addFeed(url: String, markExistingArticlesRead: Boolean) {
    feeds += Feed(
      id = "added",
      title = "added",
      feedUrl = url,
      siteUrl = null,
      etag = null,
      lastModified = null,
      lastFetchedAt = null,
      lastError = null,
      createdAt = "2026-01-01T00:00:00Z",
    )
  }
  override suspend fun renameFeed(feedId: String, name: String) = Unit
  override suspend fun deleteFeed(feedId: String) {
    feeds.removeAll { it.id == feedId }
  }
  override suspend fun createFolder(name: String) = Unit
  override suspend fun renameFolder(folderId: String, name: String) = Unit
  override suspend fun deleteFolder(folderId: String) = Unit
  override suspend fun moveFeedToFolder(feedId: String, folderId: String?) = Unit
  override suspend fun setFeedContentType(feedId: String, contentType: ContentType?) = Unit
  override suspend fun setFolderContentType(folderId: String, contentType: ContentType?) = Unit
  override suspend fun refreshFeed(feed: Feed) {
    refreshedIds += feed.id
    if (feed.id in failingRefreshIds) error("refresh failed")
  }
}
