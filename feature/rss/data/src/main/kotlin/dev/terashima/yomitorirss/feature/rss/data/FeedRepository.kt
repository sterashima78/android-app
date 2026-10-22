package dev.terashima.yomitorirss.feature.rss.data

import android.content.Context
import dev.terashima.yomitorirss.core.database.DataChangeNotifier
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.core.network.TrafficResource
import dev.terashima.yomitorirss.feature.article.ContentSourceGateway
import dev.terashima.yomitorirss.feature.article.ContentType
import dev.terashima.yomitorirss.feature.rss.Feed
import dev.terashima.yomitorirss.feature.rss.FeedFolder
import dev.terashima.yomitorirss.feature.rss.FeedInspection
import dev.terashima.yomitorirss.feature.rss.FeedRefreshContext
import dev.terashima.yomitorirss.feature.rss.FeedRepository
import dev.terashima.yomitorirss.feature.rss.RssWebScrapingPreview
import dev.terashima.yomitorirss.feature.rss.RssWebScrapingRule
import dev.terashima.yomitorirss.feature.rss.data.network.FeedClient
import dev.terashima.yomitorirss.feature.rss.data.network.WebScrapingFeedClient
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext

class DefaultFeedRepository(
  database: DatabaseConnection,
  private val contentSourceGateway: ContentSourceGateway,
  private val dataChanges: DataChangeNotifier = DataChangeNotifier(),
  applicationContext: Context? = null,
  httpClient: HttpClient = HttpClient.create(),
  private val onFeedUpdated: suspend (Feed) -> Unit = {},
) : FeedRepository {
  private val store = FeedStore(database, contentSourceGateway)
  private val refreshLocks = ConcurrentHashMap<String, Mutex>()
  private val lastAttempts = ConcurrentHashMap<String, RefreshAttempt>()
  // Failed attempts are transient: a process restart permits an immediate retry.
  private val lastFailureMillis = ConcurrentHashMap<String, Long>()
  private val _refreshingFeedIds = MutableStateFlow<Set<String>>(emptySet())
  override val refreshingFeedIds: StateFlow<Set<String>> = _refreshingFeedIds
  private val webScrapingRules = RssWebScrapingRuleStore(database)
  private val client = FeedClient(httpClient)
  private val webScrapingClient = applicationContext?.let { WebScrapingFeedClient(it.applicationContext) }

  override val changes: StateFlow<Long> = dataChanges.version

  override suspend fun listFeeds(): List<Feed> = store.listFeeds()
  override suspend fun listFolders(): List<FeedFolder> = store.listFolders()

  override suspend fun inspect(input: String): FeedInspection {
    val normalized = client.normalizeInputUrl(input)
    val customRule = findMatchingRssWebScrapingRule(webScrapingRules.list(), normalized)
    return if (customRule != null) {
      FeedInspection(directFeedUrl = normalized)
    } else {
      client.inspect(normalized)
    }
  }

  override suspend fun addFeed(url: String, markExistingArticlesRead: Boolean) {
    val normalized = client.normalizeInputUrl(url)
    val customRule = findMatchingRssWebScrapingRule(webScrapingRules.list(), normalized)
    val result = if (customRule != null) {
      requireWebScrapingClient().fetchFeed(normalized, customRule)
    } else {
      client.fetchFeed(normalized)
    }
    val feed = store.addFeed(
      parsed = requireNotNull(result.feed),
      etag = result.etag,
      modified = result.lastModified,
      markExistingArticlesRead = markExistingArticlesRead,
    )
    dataChanges.notifyChanged()
    runCatching { onFeedUpdated(feed) }
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
    refreshFeedIfDue(feed, minimumAgeMillis = 0L, failureRetryMillis = 0L)
  }

  override suspend fun refreshFeedIfDue(
    feed: Feed,
    minimumAgeMillis: Long,
    failureRetryMillis: Long,
  ): Boolean {
    require(minimumAgeMillis >= 0 && failureRetryMillis >= 0)
    // All callers (UI, periodic worker, widget and subscriptions) share this repository instance.
    val lock = refreshLocks.computeIfAbsent(feed.id) { Mutex() }
    val observedAttempt = lastAttempts[feed.id]
    return lock.withLock {
      // An overlapping caller receives the prior outcome without issuing another HTTP request.
      val completedAttempt = lastAttempts[feed.id]
      if (completedAttempt !== observedAttempt) {
        completedAttempt?.error?.let { throw it }
        return@withLock false
      }

      // Never trust the caller's snapshot: a different route may have refreshed the feed.
      val current = store.listFeeds().firstOrNull { it.id == feed.id } ?: return@withLock false
      val now = System.currentTimeMillis()
      val lastSuccess = current.lastFetchedAt?.let { timestamp ->
        runCatching { Instant.parse(timestamp).toEpochMilli() }.getOrNull()
      }
      if (minimumAgeMillis > 0 && lastSuccess != null &&
        now - lastSuccess >= 0 && now - lastSuccess < minimumAgeMillis
      ) {
        return@withLock false
      }
      val lastFailure = lastFailureMillis[feed.id]
      if (failureRetryMillis > 0 && lastFailure != null &&
        now - lastFailure >= 0 && now - lastFailure < failureRetryMillis
      ) {
        return@withLock false
      }

      _refreshingFeedIds.update { it + feed.id }
      try {
        val customRule = findMatchingRssWebScrapingRule(webScrapingRules.list(), current.feedUrl)
        val result = if (customRule != null) {
          requireWebScrapingClient().fetchFeed(current.feedUrl, customRule)
        } else {
          client.fetchFeed(current.feedUrl, current.etag, current.lastModified)
        }
        if (result.notModified) {
          store.updateFeedNotModified(current.id)
        } else {
          store.updateFeedSuccess(current, requireNotNull(result.feed), result.etag, result.lastModified)
        }
        lastFailureMillis.remove(feed.id)
        lastAttempts[feed.id] = RefreshAttempt()
        dataChanges.notifyChanged()
        runCatching { onFeedUpdated(current) }
        true
      } catch (error: CancellationException) {
        throw error
      } catch (error: Throwable) {
        store.updateFeedError(current.id, error.userMessage())
        lastFailureMillis[feed.id] = System.currentTimeMillis()
        lastAttempts[feed.id] = RefreshAttempt(error)
        dataChanges.notifyChanged()
        throw error
      } finally {
        _refreshingFeedIds.update { it - feed.id }
      }
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

  private fun requireWebScrapingClient(): WebScrapingFeedClient = requireNotNull(webScrapingClient) {
    "Web スクレイピングにはAndroidコンテキストが必要です"
  }
}

private class RefreshAttempt(val error: Throwable? = null)

private fun Throwable.userMessage(): String =
  generateSequence(this) { it.cause }
    .mapNotNull(Throwable::message)
    .firstOrNull(String::isNotBlank)
    ?: javaClass.simpleName
