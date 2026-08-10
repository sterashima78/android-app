package dev.terashima.yomitorirss.feature.youtube.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dev.terashima.yomitorirss.feature.youtube.YouTubeChannel
import dev.terashima.yomitorirss.feature.youtube.YouTubeVideo

internal class YouTubeDatabase(context: Context) :
  SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

  override fun onCreate(db: SQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE channels (
        channel_id TEXT PRIMARY KEY NOT NULL,
        title TEXT NOT NULL,
        channel_url TEXT NOT NULL,
        added_at INTEGER NOT NULL
      )
      """.trimIndent(),
    )
    db.execSQL(
      """
      CREATE TABLE videos (
        video_id TEXT PRIMARY KEY NOT NULL,
        channel_id TEXT NOT NULL,
        title TEXT NOT NULL,
        video_url TEXT NOT NULL,
        published_at INTEGER NOT NULL,
        is_read INTEGER NOT NULL DEFAULT 0,
        is_watch_later INTEGER NOT NULL DEFAULT 0,
        FOREIGN KEY(channel_id) REFERENCES channels(channel_id) ON DELETE CASCADE
      )
      """.trimIndent(),
    )
    db.execSQL("CREATE INDEX videos_channel_idx ON videos(channel_id)")
    db.execSQL("CREATE INDEX videos_unread_idx ON videos(is_read, is_watch_later, published_at DESC)")
    db.execSQL("CREATE INDEX videos_watch_later_idx ON videos(is_watch_later, published_at DESC)")
  }

  override fun onConfigure(db: SQLiteDatabase) {
    super.onConfigure(db)
    db.setForeignKeyConstraintsEnabled(true)
  }

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    if (oldVersion < 2) {
      db.execSQL("ALTER TABLE videos ADD COLUMN is_watch_later INTEGER NOT NULL DEFAULT 0")
      db.execSQL("DROP INDEX IF EXISTS videos_unread_idx")
      db.execSQL("CREATE INDEX videos_unread_idx ON videos(is_read, is_watch_later, published_at DESC)")
      db.execSQL("CREATE INDEX videos_watch_later_idx ON videos(is_watch_later, published_at DESC)")
    }
  }

  fun listChannels(): List<YouTubeChannel> = readableDatabase.query(
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

  private fun listVideos(whereClause: String): List<YouTubeVideo> = readableDatabase.rawQuery(
    """
    SELECT v.video_id, v.channel_id, c.title, v.title, v.video_url, v.published_at, v.is_read, v.is_watch_later
    FROM videos v
    JOIN channels c ON c.channel_id = v.channel_id
    WHERE $whereClause
    ORDER BY v.published_at DESC
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

  fun upsertFeed(feed: ParsedYouTubeFeed): YouTubeChannel {
    val db = writableDatabase
    val channel = YouTubeChannel(
      id = feed.channelId,
      title = feed.channelTitle,
      url = YouTubeChannelUrl.canonical(feed.channelId),
    )
    db.beginTransaction()
    try {
      val insertValues = ContentValues().apply {
        put("channel_id", channel.id)
        put("title", channel.title)
        put("channel_url", channel.url)
        put("added_at", System.currentTimeMillis())
      }
      db.insertWithOnConflict("channels", null, insertValues, SQLiteDatabase.CONFLICT_IGNORE)
      val channelValues = ContentValues().apply {
        put("title", channel.title)
        put("channel_url", channel.url)
      }
      db.update("channels", channelValues, "channel_id = ?", arrayOf(channel.id))

      feed.videos.forEach { video ->
        val exists = db.rawQuery(
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
          db.update("videos", values, "video_id = ?", arrayOf(video.id))
        } else {
          db.insertOrThrow("videos", null, values)
        }
      }
      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
    return channel
  }

  fun deleteChannel(channelId: String) {
    writableDatabase.delete("channels", "channel_id = ?", arrayOf(channelId))
  }

  fun markRead(videoId: String) {
    val values = ContentValues().apply {
      put("is_read", 1)
      put("is_watch_later", 0)
    }
    writableDatabase.update("videos", values, "video_id = ?", arrayOf(videoId))
  }

  fun setWatchLater(videoId: String, watchLater: Boolean) {
    val values = ContentValues().apply {
      put("is_watch_later", if (watchLater) 1 else 0)
      if (watchLater) put("is_read", 0)
    }
    writableDatabase.update("videos", values, "video_id = ?", arrayOf(videoId))
  }

  fun markAllRead() {
    val values = ContentValues().apply { put("is_read", 1) }
    writableDatabase.update("videos", values, "is_read = 0 AND is_watch_later = 0", null)
  }

  private companion object {
    const val DATABASE_NAME = "youtube.db"
    const val DATABASE_VERSION = 2
  }
}
