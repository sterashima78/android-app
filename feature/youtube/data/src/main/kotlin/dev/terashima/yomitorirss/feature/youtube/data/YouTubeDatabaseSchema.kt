package dev.terashima.yomitorirss.feature.youtube.data

import dev.terashima.yomitorirss.core.database.DatabaseSchemaContribution

val youtubeDatabaseSchema = DatabaseSchemaContribution(
  owner = "youtube",
  createSchema = { db ->
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS channels (
        channel_id TEXT PRIMARY KEY NOT NULL,
        title TEXT NOT NULL,
        channel_url TEXT NOT NULL,
        added_at INTEGER NOT NULL
      )
      """.trimIndent(),
    )
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS videos (
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
    db.execSQL("CREATE INDEX IF NOT EXISTS videos_channel_idx ON videos(channel_id)")
    db.execSQL("CREATE INDEX IF NOT EXISTS videos_unread_idx ON videos(is_read, is_watch_later, published_at DESC)")
    db.execSQL("CREATE INDEX IF NOT EXISTS videos_watch_later_idx ON videos(is_watch_later, published_at DESC)")
  },
)
