package dev.terashima.yomitorirss.feature.youtube.data

import dev.terashima.yomitorirss.feature.video.VideoProvider
import dev.terashima.yomitorirss.feature.video.VideoProviderRepository
import dev.terashima.yomitorirss.feature.video.VideoProviderType
import dev.terashima.yomitorirss.feature.video.VideoProviderVideo
import dev.terashima.yomitorirss.feature.youtube.YouTubeChannel
import dev.terashima.yomitorirss.feature.youtube.YouTubeRepository
import dev.terashima.yomitorirss.feature.youtube.YouTubeVideo

class DefaultYouTubeRepository(
  private val providers: VideoProviderRepository,
) : YouTubeRepository {
  override suspend fun listChannels(): List<YouTubeChannel> {
    val provider = youtubeProvider() ?: return emptyList()
    return providers.subscriptions(provider.id).map { subscription ->
      YouTubeChannel(
        id = subscription.sourceId,
        title = subscription.title,
        url = subscription.sourceUrl,
      )
    }
  }

  override suspend fun listUnreadVideos(): List<YouTubeVideo> = youtubeVideos(providers.unreadVideos())

  override suspend fun listWatchLaterVideos(): List<YouTubeVideo> = youtubeVideos(providers.watchLaterVideos())

  override suspend fun listHistoryVideos(): List<YouTubeVideo> = youtubeVideos(providers.historyVideos())

  override suspend fun subscribe(channelUrl: String): YouTubeChannel {
    val provider = youtubeProvider() ?: providers.saveProvider(
      VideoProvider(
        id = "youtube",
        type = VideoProviderType.YOUTUBE,
        name = "YouTube",
        enabled = true,
      ),
    )
    val subscription = providers.subscribe(provider.id, channelUrl)
    return YouTubeChannel(subscription.sourceId, subscription.title, subscription.sourceUrl)
  }

  override suspend fun unsubscribe(channelId: String) {
    val provider = youtubeProvider() ?: return
    providers.subscriptions(provider.id)
      .firstOrNull { it.sourceId == channelId }
      ?.let { providers.unsubscribe(it.id) }
  }

  override suspend fun refresh() {
    youtubeProvider()?.let { providers.refreshProviders(it.id) }
  }

  override suspend fun markRead(videoId: String) {
    youtubeProvider()?.let { providers.markRead(providerVideoId(it.id, videoId)) }
  }

  override suspend fun markUnread(videoId: String) {
    youtubeProvider()?.let { providers.markUnread(providerVideoId(it.id, videoId)) }
  }

  override suspend fun setWatchLater(videoId: String, watchLater: Boolean) {
    youtubeProvider()?.let { providers.setWatchLater(providerVideoId(it.id, videoId), watchLater) }
  }

  override suspend fun markAllRead() {
    providers.markAllRead()
  }

  private fun youtubeProvider(): VideoProvider? = providers.providers().firstOrNull {
    it.type == VideoProviderType.YOUTUBE
  }

  private fun youtubeVideos(videos: List<VideoProviderVideo>): List<YouTubeVideo> {
    val provider = youtubeProvider() ?: return emptyList()
    val subscriptions = providers.subscriptions(provider.id).associateBy { it.id }
    return videos.asSequence()
      .filter { it.providerId == provider.id }
      .map { item ->
        val subscription = item.subscriptionId?.let(subscriptions::get)
        YouTubeVideo(
          id = item.providerItemId,
          channelId = subscription?.sourceId.orEmpty(),
          channelTitle = item.subscriptionTitle.orEmpty(),
          title = item.video.title,
          url = item.video.pageUrl.orEmpty(),
          publishedAtEpochMillis = item.publishedAtEpochMillis,
          isRead = item.isRead,
          isWatchLater = item.isWatchLater,
        )
      }
      .toList()
  }
}

private fun providerVideoId(providerId: String, providerItemId: String): String =
  "provider:$providerId:$providerItemId"
