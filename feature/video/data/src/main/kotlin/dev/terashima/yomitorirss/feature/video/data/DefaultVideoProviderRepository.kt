package dev.terashima.yomitorirss.feature.video.data

import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.feature.video.VideoProvider
import dev.terashima.yomitorirss.feature.video.VideoProviderRefreshResult
import dev.terashima.yomitorirss.feature.video.VideoProviderRepository
import dev.terashima.yomitorirss.feature.video.VideoProviderType
import dev.terashima.yomitorirss.feature.video.VideoProviderVideo
import dev.terashima.yomitorirss.feature.video.VideoSubscription
import java.io.IOException

class DefaultVideoProviderRepository(
  database: DatabaseConnection,
  httpClient: HttpClient,
) : VideoProviderRepository {
  private val database = VideoProviderDatabase(database)
  private val youtubeClient = YouTubeVideoProviderClient(httpClient)

  override fun providers(): List<VideoProvider> = database.providers()

  override fun saveProvider(provider: VideoProvider): VideoProvider = database.saveProvider(provider)

  override fun deleteProvider(id: String) = database.deleteProvider(id)

  override fun subscriptions(providerId: String?): List<VideoSubscription> = database.subscriptions(providerId)

  override suspend fun subscribe(providerId: String, sourceUrl: String): VideoSubscription {
    val provider = database.requireProvider(providerId)
    require(provider.enabled) { "動画プロバイダが無効です" }
    val feed = when (provider.type) {
      VideoProviderType.YOUTUBE -> youtubeClient.subscribe(sourceUrl)
    }
    return database.upsertProviderFeed(provider, feed).first
  }

  override suspend fun unsubscribe(subscriptionId: String) = database.unsubscribe(subscriptionId)

  override suspend fun refreshProviders(providerId: String?): VideoProviderRefreshResult {
    val activeProviders = database.providers().filter { it.enabled && (providerId == null || it.id == providerId) }
    var refreshed = 0
    var added = 0
    var failed = 0
    var attempted = 0
    activeProviders.forEach { provider ->
      database.subscriptions(provider.id).forEach { subscription ->
        attempted += 1
        runCatching {
          val feed = when (provider.type) {
            VideoProviderType.YOUTUBE -> youtubeClient.refresh(subscription.sourceId)
          }
          database.upsertProviderFeed(provider, feed).second
        }.fold(
          onSuccess = { addedCount ->
            refreshed += 1
            added += addedCount
          },
          onFailure = { failed += 1 },
        )
      }
    }
    if (failed > 0 && refreshed == 0 && attempted > 0) {
      throw IOException("動画プロバイダを更新できませんでした")
    }
    return VideoProviderRefreshResult(refreshed, added, failed)
  }

  override fun unreadVideos(): List<VideoProviderVideo> = database.unreadVideos()

  override fun watchLaterVideos(): List<VideoProviderVideo> = database.watchLaterVideos()

  override fun historyVideos(limit: Int): List<VideoProviderVideo> = database.historyVideos(limit)

  override fun markRead(videoId: String) = database.markRead(videoId)

  override fun markUnread(videoId: String) = database.markUnread(videoId)

  override fun setWatchLater(videoId: String, watchLater: Boolean) = database.setWatchLater(videoId, watchLater)

  override fun markAllRead() = database.markAllRead()
}
