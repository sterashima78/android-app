package dev.terashima.yomitorirss.feature.youtube

data class YouTubeChannel(
  val id: String,
  val title: String,
  val url: String,
)

data class YouTubeVideo(
  val id: String,
  val channelId: String,
  val channelTitle: String,
  val title: String,
  val url: String,
  val publishedAtEpochMillis: Long,
  val isRead: Boolean,
  val isWatchLater: Boolean,
) {
  val thumbnailUrl: String
    get() = "https://i.ytimg.com/vi/$id/hqdefault.jpg"
}

interface YouTubeRepository {
  suspend fun listChannels(): List<YouTubeChannel>
  suspend fun listUnreadVideos(): List<YouTubeVideo>
  suspend fun listWatchLaterVideos(): List<YouTubeVideo>
  suspend fun subscribe(channelUrl: String): YouTubeChannel
  suspend fun unsubscribe(channelId: String)
  suspend fun refresh()
  suspend fun markRead(videoId: String)
  suspend fun setWatchLater(videoId: String, watchLater: Boolean)
  suspend fun markAllRead()
}
