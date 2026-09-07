package dev.terashima.yomitorirss.feature.video.data

import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DatabaseSchemaContribution

val videoDatabaseSchema = DatabaseSchemaContribution(
  owner = "video",
  createSchema = ::ensureVideoSchema,
)

internal fun ensureVideoSchema(db: SQLiteDatabase) {
  db.execSQL(
    """
      CREATE TABLE IF NOT EXISTS video_items (
        id TEXT PRIMARY KEY NOT NULL,
        source TEXT NOT NULL,
        source_id TEXT NOT NULL,
        title TEXT NOT NULL,
        page_url TEXT,
        thumbnail_url TEXT,
        duration_ms INTEGER,
        size_bytes INTEGER,
        mime_type TEXT,
        updated_at INTEGER NOT NULL,
        UNIQUE(source, source_id)
      )
    """.trimIndent(),
  )
  db.execSQL(
    """
      CREATE TABLE IF NOT EXISTS video_playback_state (
        video_id TEXT PRIMARY KEY NOT NULL,
        position_ms INTEGER NOT NULL,
        duration_ms INTEGER NOT NULL,
        last_played_at INTEGER NOT NULL,
        completed INTEGER NOT NULL DEFAULT 0,
        FOREIGN KEY(video_id) REFERENCES video_items(id) ON DELETE CASCADE
      )
    """.trimIndent(),
  )
  db.execSQL(
    """
      CREATE TABLE IF NOT EXISTS video_web_extractor_rules (
        id TEXT PRIMARY KEY NOT NULL,
        url_pattern TEXT NOT NULL,
        title_function TEXT,
        thumbnail_function TEXT,
        playback_function TEXT,
        timeout_seconds INTEGER NOT NULL DEFAULT 15,
        updated_at INTEGER NOT NULL
      )
    """.trimIndent(),
  )
  db.execSQL(
    "CREATE INDEX IF NOT EXISTS idx_video_items_source_updated ON video_items(source, updated_at DESC)",
  )
  db.execSQL(
    "CREATE INDEX IF NOT EXISTS idx_video_rules_updated ON video_web_extractor_rules(updated_at DESC)",
  )
}
