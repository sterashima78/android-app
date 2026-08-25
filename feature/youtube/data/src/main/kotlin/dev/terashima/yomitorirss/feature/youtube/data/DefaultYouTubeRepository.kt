package dev.terashima.yomitorirss.feature.youtube.data

import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.core.network.HttpRequest
import dev.terashima.yomitorirss.feature.youtube.YouTubeChannel
import dev.terashima.yomitorirss.feature.youtube.YouTubeRepository
import dev.terashima.yomitorirss.feature.youtube.YouTubeVideo
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DefaultYouTubeRepository(
  database: DatabaseConnection,
  private val httpClient: HttpClient = HttpClient.create(),
) : YouTubeRepository {
  private val database = YouTubeDatabase(database)
  private val parser = YouTubeFeedParser()

  override suspend fun listChannels(): List<YouTubeChannel> = withContext(Dispatchers.IO) {
    database.listChannels()
  }

  override suspend fun listUnreadVideos(): List<YouTubeVideo> = withContext(Dispatchers.IO) {
    database.listUnreadVideos()
  }

  override suspend fun listWatchLaterVideos(): List<YouTubeVideo> = withContext(Dispatchers.IO) {
    database.listWatchLaterVideos()
  }

  override suspend fun listHistoryVideos(): List<YouTubeVideo> = withContext(Dispatchers.IO) {
    database.listHistoryVideos()
  }

  override suspend fun subscribe(channelUrl: String): YouTubeChannel = withContext(Dispatchers.IO) {
    val requestedChannelId = YouTubeChannelUrl.channelId(channelUrl)
    val feed = fetchFeed(requestedChannelId)
    require(feed.channelId == requestedChannelId) {
      "YouTubeチャンネルIDが取得結果と一致しません"
    }
    database.upsertFeed(feed)
  }

  override suspend fun unsubscribe(channelId: String) = withContext(Dispatchers.IO) {
    database.deleteChannel(channelId)
  }

  override suspend fun refresh() = withContext(Dispatchers.IO) {
    val channels = database.listChannels()
    var failures = 0
    channels.forEach { channel ->
      runCatching { database.upsertFeed(fetchFeed(channel.id)) }
        .onFailure { failures += 1 }
    }
    if (failures > 0) {
      throw IOException("${channels.size}件中${failures}件のYouTubeチャンネルを更新できませんでした")
    }
  }

  override suspend fun markRead(videoId: String) = withContext(Dispatchers.IO) {
    database.markRead(videoId)
  }

  override suspend fun markUnread(videoId: String) = withContext(Dispatchers.IO) {
    database.markUnread(videoId)
  }

  override suspend fun setWatchLater(videoId: String, watchLater: Boolean) = withContext(Dispatchers.IO) {
    database.setWatchLater(videoId, watchLater)
  }

  override suspend fun markAllRead() = withContext(Dispatchers.IO) {
    database.markAllRead()
  }

  private suspend fun fetchFeed(channelId: String): ParsedYouTubeFeed {
    val response = httpClient.execute(HttpRequest(url = YouTubeChannelUrl.feed(channelId), maxResponseBytes = 4L * 1024 * 1024))
    if (!response.isSuccessful) {
      throw IOException("YouTubeの取得に失敗しました: HTTP ${response.statusCode}")
    }
    return parser.parse(response.body)
  }
}
