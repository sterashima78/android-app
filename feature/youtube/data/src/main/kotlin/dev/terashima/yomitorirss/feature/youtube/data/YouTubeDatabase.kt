package dev.terashima.yomitorirss.feature.youtube.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.youtube.YouTubeChannel
import dev.terashima.yomitorirss.feature.youtube.YouTubeVideo

internal class YouTubeDatabase(
  private val database: DatabaseConnection,
) {
  fun listChannels(): List<YouTubeChannel> = database.readable.query(
    "channels",
    arrayOf("channel_id", "title", "channel_url"),
    null,
    null,
    null,
    null,
    "title COLLATE NOCASE ASC",
  ).use { cursor ->
    buildList {
      while (cursor.moveToNext()) {
        add(
          YouTubeChannel(
            id = cursor.getString(0),
            title = cursor.getString(1),
            url = cursor.getString(2),
          ),
        )
      }
    }
  }

  fun listUnreadVideos(): List<YouTubeVideo> = listVideos(
    whereClause = "v.is_read = 0 AND v.is_watch_later = 0",
  )

  fun listWatchLaterVideos(): List<YouTubeVideo> = listVideos(
    whereClause = "v.is_watch_later = 1",
  )

  fun listHistoryVideos(): List<YouTubeVideo> = listVideos(
    whereClause = "v.is_read = 1 AND v.is_watch_later = 0",
    limit = 500,
  )

  private fun listVideos(whereClause: String, limit: Int? = null): List<YouTubeVideo> {
    val limitClause = limit?.let { "LIMIT $it" }.orEmpty()
    return database.readable.rawQuery(
      """
      SELECT v.video_id, v.channel_id, c.title, v.title, v.video_url, v.published_at, v.is_read, v.is_watch_later
      FROM videos v
      JOIN channels c ON c.channel_id = v.channel_id
      WHERE $whereClause
      ORDER BY v.published_at DESC
      $limitClause
      """.trimIndent(),
      null,
    ).use { cursor ->
      buildList {
        while (cursor.moveToNext()) {
          add(
            YouTubeVideo(
              id = cursor.getString(0),
              channelId = cursor.getString(1),
              channelTitle = cursor.getString(2),
              title = cursor.getString(3),
              url = cursor.getString(4),
              publishedAtEpochMillis = cursor.getLong(5),
              isRead = cursor.getInt(6) != 0,
              isWatchLater = cursor.getInt(7) != 0,
            ),
          )
        }
      }
    }
  }

  fun upsertFeed(feed: ParsedYouTubeFeed): YouTubeChannel {
    val channel = YouTubeChannel(
      id = feed.channelId,
      title = feed.channelTitle,
      url = YouTubeChannelUrl.canonical(feed.channelId),
    )
    database.transaction {
      val insertValues = ContentValues().apply {
        put("channel_id", channel.id)
        put("title", channel.title)
        put("channel_url", channel.url)
        put("added_at", System.currentTimeMillis())
      }
      insertWithOnConflict("channels", null, insertValues, SQLiteDatabase.CONFLICT_IGNORE)
      val channelValues = ContentValues().apply {
        put("title", channel.title)
        put("channel_url", channel.url)
      }
      update("channels", channelValues, "channel_id = ?", arrayOf(channel.id))

      feed.videos.forEach { video ->
        val exists = rawQuery(
          "SELECT 1 FROM videos WHERE video_id = ? LIMIT 1",
          arrayOf(video.id),
        ).use { it.moveToFirst() }
        val values = ContentValues().apply {
          put("video_id", video.id)
          put("channel_id", video.channelId)
          put("title", video.title)
          put("video_url", video.url)
          put("published_at", video.publishedAtEpochMillis)
          if (!exists) {
            put("is_read", 0)
            put("is_watch_later", 0)
          }
        }
        if (exists) {
          update("videos", values, "video_id = ?", arrayOf(video.id))
        } else {
          insertOrThrow("videos", null, values)
        }
      }
    }
    return channel
  }

  fun deleteChannel(channelId: String) {
    database.writable.delete("channels", "channel_id = ?", arrayOf(channelId))
  }

  fun markRead(videoId: String) {
    val values = ContentValues().apply {
      put("is_read", 1)
      put("is_watch_later", 0)
    }
    database.writable.update("videos", values, "video_id = ?", arrayOf(videoId))
  }

  fun markUnread(videoId: String) {
    val values = ContentValues().apply {
      put("is_read", 0)
      put("is_watch_later", 0)
    }
    database.writable.update("videos", values, "video_id = ?", arrayOf(videoId))
  }

  fun setWatchLater(videoId: String, watchLater: Boolean) {
    val values = ContentValues().apply {
      put("is_watch_later", if (watchLater) 1 else 0)
      if (watchLater) put("is_read", 0)
    }
    database.writable.update("videos", values, "video_id = ?", arrayOf(videoId))
  }

  fun markAllRead() {
    val values = ContentValues().apply { put("is_read", 1) }
    database.writable.update("videos", values, "is_read = 0 AND is_watch_later = 0", null)
  }
}
