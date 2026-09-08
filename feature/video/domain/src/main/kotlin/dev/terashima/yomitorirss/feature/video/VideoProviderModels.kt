package dev.terashima.yomitorirss.feature.video

enum class VideoProviderType {
  YOUTUBE,
}

data class VideoProvider(
  val id: String,
  val type: VideoProviderType,
  val name: String,
  val enabled: Boolean = true,
  val createdAtEpochMillis: Long = 0L,
  val updatedAtEpochMillis: Long = 0L,
)

data class VideoSubscription(
  val id: String,
  val providerId: String,
  val sourceId: String,
  val title: String,
  val sourceUrl: String,
  val createdAtEpochMillis: Long = 0L,
  val updatedAtEpochMillis: Long = 0L,
)

data class VideoProviderVideo(
  val video: VideoItem,
  val providerId: String,
  val providerItemId: String,
  val subscriptionId: String?,
  val subscriptionTitle: String?,
  val publishedAtEpochMillis: Long,
  val isRead: Boolean,
  val isWatchLater: Boolean,
)

data class VideoProviderRefreshResult(
  val refreshedSubscriptions: Int,
  val addedVideos: Int,
  val failedSubscriptions: Int,
)

interface VideoProviderRepository {
  fun providers(): List<VideoProvider>

  fun saveProvider(provider: VideoProvider): VideoProvider

  fun deleteProvider(id: String)

  fun subscriptions(providerId: String? = null): List<VideoSubscription>

  suspend fun subscribe(providerId: String, sourceUrl: String): VideoSubscription

  suspend fun unsubscribe(subscriptionId: String)

  suspend fun refreshProviders(providerId: String? = null): VideoProviderRefreshResult

  fun unreadVideos(): List<VideoProviderVideo>

  fun watchLaterVideos(): List<VideoProviderVideo>

  fun historyVideos(limit: Int = 500): List<VideoProviderVideo>

  fun markRead(videoId: String)

  fun markUnread(videoId: String)

  fun setWatchLater(videoId: String, watchLater: Boolean)

  fun markAllRead(providerId: String? = null)
}
