package dev.terashima.yomitorirss.feature.rss.data

import android.content.Context
import dev.terashima.yomitorirss.core.database.DataChangeNotifier
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.feature.article.ContentSourceGateway
import dev.terashima.yomitorirss.feature.article.ContentType
import dev.terashima.yomitorirss.feature.rss.Feed
import dev.terashima.yomitorirss.feature.rss.FeedFolder
import dev.terashima.yomitorirss.feature.rss.FeedInspection
import dev.terashima.yomitorirss.feature.rss.FeedRepository
import dev.terashima.yomitorirss.feature.rss.RssWebScrapingPreview
import dev.terashima.yomitorirss.feature.rss.RssWebScrapingRule
import dev.terashima.yomitorirss.feature.rss.data.network.FeedClient
import dev.terashima.yomitorirss.feature.rss.data.network.MangaOneFeedClient
import dev.terashima.yomitorirss.feature.rss.data.network.WebScrapingFeedClient
import dev.terashima.yomitorirss.feature.rss.data.network.YanmagaFeedClient
import kotlinx.coroutines.flow.StateFlow

class DefaultFeedRepository(
  database: DatabaseConnection,
  private val contentSourceGateway: ContentSourceGateway,
  private val dataChanges: DataChangeNotifier = DataChangeNotifier(),
  applicationContext: Context? = null,
  httpClient: HttpClient = HttpClient.create(),
) : FeedRepository {
  private val store = FeedStore(database, contentSourceGateway)
  private val webScrapingRules = RssWebScrapingRuleStore(database)
  private val client = FeedClient(httpClient)
  private val yanmagaClient = YanmagaFeedClient(httpClient)
  private val mangaOneClient = applicationContext?.let { MangaOneFeedClient(it.applicationContext) }
  private val webScrapingClient = applicationContext?.let { WebScrapingFeedClient(it.applicationContext) }

  override val changes: StateFlow<Long> = dataChanges.version

  override suspend fun listFeeds(): List<Feed> = store.listFeeds()
  override suspend fun listFolders(): List<FeedFolder> = store.listFolders()

  override suspend fun inspect(input: String): FeedInspection {
    val normalized = client.normalizeInputUrl(input)
    val customRule = findMatchingRssWebScrapingRule(webScrapingRules.list(), normalized)
    return when {
      customRule != null -> FeedInspection(directFeedUrl = normalized)
      yanmagaClient.supports(normalized) -> yanmagaClient.inspect(normalized)
      MangaOneFeedClient.companionSupports(normalized) -> requireMangaOneClient().inspect(normalized)
      else -> client.inspect(normalized)
    }
  }

  override suspend fun addFeed(url: String, markExistingArticlesRead: Boolean) {
    val normalized = client.normalizeInputUrl(url)
    val customRule = findMatchingRssWebScrapingRule(webScrapingRules.list(), normalized)
    val isYanmaga = yanmagaClient.supports(normalized)
    val isMangaOne = MangaOneFeedClient.companionSupports(normalized)
    val result = when {
      customRule != null -> requireWebScrapingClient().fetchFeed(normalized, customRule)
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

  override suspend fun renameFeed(feedId: String, name: String) {
    store.renameFeed(feedId, name)
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
      val customRule = findMatchingRssWebScrapingRule(webScrapingRules.list(), feed.feedUrl)
      val result = when {
        customRule != null -> requireWebScrapingClient().fetchFeed(feed.feedUrl, customRule)
        yanmagaClient.supports(feed.feedUrl) -> yanmagaClient.fetchFeed(feed.feedUrl, feed.etag, feed.lastModified)
        MangaOneFeedClient.companionSupports(feed.feedUrl) -> requireMangaOneClient().fetchFeed(feed.feedUrl, feed.etag, feed.lastModified)
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

  override fun listWebScrapingRules(): List<RssWebScrapingRule> = webScrapingRules.list()

  override fun saveWebScrapingRule(
    id: String?,
    urlPattern: String,
    functionCode: String,
    timeoutSeconds: Int,
  ): RssWebScrapingRule = webScrapingRules.save(id, urlPattern, functionCode, timeoutSeconds).also {
    dataChanges.notifyChanged()
  }

  override fun deleteWebScrapingRule(id: String) {
    webScrapingRules.delete(id)
    dataChanges.notifyChanged()
  }

  override suspend fun testWebScrapingRule(
    urlPattern: String,
    functionCode: String,
    timeoutSeconds: Int,
    url: String,
  ): RssWebScrapingPreview {
    val normalizedUrl = normalizeRssWebScrapingUrl(url)
    validateRssWebScrapingRule(urlPattern.trim(), functionCode.trim(), timeoutSeconds)
    require(rssWebScrapingUrlPatternMatches(urlPattern, normalizedUrl)) {
      "テスト URL が URL パターンに一致していません"
    }
    val draftRule = RssWebScrapingRule(
      id = "test",
      urlPattern = urlPattern.trim(),
      functionCode = functionCode.trim(),
      timeoutSeconds = timeoutSeconds,
      updatedAt = System.currentTimeMillis(),
    )
    return requireWebScrapingClient().test(normalizedUrl, draftRule)
  }

  private fun requireMangaOneClient(): MangaOneFeedClient = requireNotNull(mangaOneClient) {
    "マンガワンのフィード取得にはAndroidコンテキストが必要です"
  }

  private fun requireWebScrapingClient(): WebScrapingFeedClient = requireNotNull(webScrapingClient) {
    "Web スクレイピングにはAndroidコンテキストが必要です"
  }
}

private fun Throwable.userMessage(): String =
  generateSequence(this) { it.cause }
    .mapNotNull(Throwable::message)
    .firstOrNull(String::isNotBlank)
    ?: javaClass.simpleName
