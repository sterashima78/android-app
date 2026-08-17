package dev.terashima.yomitorirss.feature.rss.data

import android.content.Context
import dev.terashima.yomitorirss.core.database.DataChangeNotifier
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.article.ContentType
import dev.terashima.yomitorirss.feature.rss.Feed
import dev.terashima.yomitorirss.feature.rss.FeedFolder
import dev.terashima.yomitorirss.feature.rss.FeedInspection
import dev.terashima.yomitorirss.feature.rss.FeedRepository
import dev.terashima.yomitorirss.feature.rss.data.network.FeedClient
import dev.terashima.yomitorirss.feature.rss.data.network.MangaOneFeedClient
import dev.terashima.yomitorirss.feature.rss.data.network.YanmagaFeedClient
import kotlinx.coroutines.flow.StateFlow

class DefaultFeedRepository(
  database: DatabaseConnection,
  private val dataChanges: DataChangeNotifier = DataChangeNotifier(),
  applicationContext: Context? = null,
) : FeedRepository {
  private val store = FeedStore(database)
  private val client = FeedClient()
  private val yanmagaClient = YanmagaFeedClient()
  private val mangaOneClient = applicationContext?.let { MangaOneFeedClient(it.applicationContext) }

  override val changes: StateFlow<Long> = dataChanges.version

  override suspend fun listFeeds(): List<Feed> = store.listFeeds()

  override suspend fun listFolders(): List<FeedFolder> = store.listFolders()

  override suspend fun inspect(input: String): FeedInspection {
    val normalized = client.normalizeInputUrl(input)
    return when {
      yanmagaClient.supports(normalized) -> yanmagaClient.inspect(normalized)
      MangaOneFeedClient.companionSupports(normalized) -> requireMangaOneClient().inspect(normalized)
      else -> client.inspect(normalized)
    }
  }

  override suspend fun addFeed(url: String, markExistingArticlesRead: Boolean) {
    val normalized = client.normalizeInputUrl(url)
    val isYanmaga = yanmagaClient.supports(normalized)
    val isMangaOne = MangaOneFeedClient.companionSupports(normalized)
    val result = when {
      isYanmaga -> yanmagaClient.fetchFeed(normalized)
      isMangaOne -> requireMangaOneClient().fetchFeed(normalized)
      else -> client.fetchFeed(normalized)
    }
    store.addFeed(
      parsed = requireNotNull(result.feed),
      etag = result.etag,
      modified = result.lastModified,
      markExistingArticlesRead = markExistingArticlesRead,
      contentTypeOverride = ContentType.COMIC.takeIf { isYanmaga || isMangaOne },
    )
    dataChanges.notifyChanged()
  }

  override suspend fun deleteFeed(feedId: String) {
    store.deleteFeed(feedId)
    dataChanges.notifyChanged()
  }

  override suspend fun createFolder(name: String) {
    store.createFolder(name)
    dataChanges.notifyChanged()
  }

  override suspend fun renameFolder(folderId: String, name: String) {
    store.renameFolder(folderId, name)
    dataChanges.notifyChanged()
  }

  override suspend fun deleteFolder(folderId: String) {
    store.deleteFolder(folderId)
    dataChanges.notifyChanged()
  }

  override suspend fun moveFeedToFolder(feedId: String, folderId: String?) {
    store.moveFeedToFolder(feedId, folderId)
    dataChanges.notifyChanged()
  }

  override suspend fun setFeedContentType(feedId: String, contentType: ContentType?) {
    store.setFeedContentType(feedId, contentType)
    dataChanges.notifyChanged()
  }

  override suspend fun setFolderContentType(folderId: String, contentType: ContentType?) {
    store.setFolderContentType(folderId, contentType)
    dataChanges.notifyChanged()
  }

  override suspend fun refreshFeed(feed: Feed) {
    try {
      val result = when {
        yanmagaClient.supports(feed.feedUrl) -> yanmagaClient.fetchFeed(feed.feedUrl, feed.etag, feed.lastModified)
        MangaOneFeedClient.companionSupports(feed.feedUrl) -> requireMangaOneClient().fetchFeed(
          feed.feedUrl,
          feed.etag,
          feed.lastModified,
        )
        else -> client.fetchFeed(feed.feedUrl, feed.etag, feed.lastModified)
      }
      if (result.notModified) {
        store.updateFeedNotModified(feed.id)
      } else {
        store.updateFeedSuccess(feed, requireNotNull(result.feed), result.etag, result.lastModified)
      }
      dataChanges.notifyChanged()
    } catch (error: Throwable) {
      store.updateFeedError(feed.id, error.userMessage())
      dataChanges.notifyChanged()
      throw error
    }
  }

  private fun requireMangaOneClient(): MangaOneFeedClient = requireNotNull(mangaOneClient) {
    "マンガワンのフィード取得にはAndroidコンテキストが必要です"
  }
}

private fun Throwable.userMessage(): String =
  generateSequence(this) { it.cause }
    .mapNotNull(Throwable::message)
    .firstOrNull(String::isNotBlank)
    ?: javaClass.simpleName
