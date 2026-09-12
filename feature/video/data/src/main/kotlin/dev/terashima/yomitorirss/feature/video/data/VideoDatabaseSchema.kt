package dev.terashima.yomitorirss.feature.video.data

import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DatabaseMigration
import dev.terashima.yomitorirss.core.database.DatabaseMigrationPhase
import dev.terashima.yomitorirss.core.database.DatabaseSchemaContribution

val videoDatabaseSchema = DatabaseSchemaContribution(
  owner = "video",
  createSchema = ::ensureVideoSchema,
  migrations = listOf(
    DatabaseMigration(
      targetVersion = 32,
      phase = DatabaseMigrationPhase.BEFORE_SCHEMA,
      migrate = ::migrateCustomVideoProviderCode,
    ),
  ),
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
      CREATE TABLE IF NOT EXISTS video_smb_sources (
        id TEXT PRIMARY KEY NOT NULL,
        server_id TEXT NOT NULL,
        share_name TEXT NOT NULL,
        root_path TEXT NOT NULL,
        updated_at INTEGER NOT NULL,
        UNIQUE(server_id, share_name, root_path)
      )
    """.trimIndent(),
  )
  db.execSQL(
    """
      CREATE TABLE IF NOT EXISTS video_folders (
        id TEXT PRIMARY KEY NOT NULL,
        name TEXT NOT NULL,
        normalized_name TEXT NOT NULL UNIQUE,
        created_at INTEGER NOT NULL,
        updated_at INTEGER NOT NULL
      )
    """.trimIndent(),
  )
  db.execSQL(
    """
      CREATE TABLE IF NOT EXISTS video_saved_items (
        video_id TEXT PRIMARY KEY NOT NULL,
        folder_id TEXT,
        saved_at INTEGER NOT NULL,
        FOREIGN KEY(video_id) REFERENCES video_items(id) ON DELETE CASCADE,
        FOREIGN KEY(folder_id) REFERENCES video_folders(id) ON DELETE SET NULL
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
        share_cookies_for_playback INTEGER NOT NULL DEFAULT 0,
        share_referrer_path_for_playback INTEGER NOT NULL DEFAULT 0,
        timeout_seconds INTEGER NOT NULL DEFAULT 15,
        updated_at INTEGER NOT NULL
      )
    """.trimIndent(),
  )
  ensureVideoWebExtractorRuleCookieColumn(db)
  ensureVideoWebExtractorRuleReferrerPathColumn(db)
  db.execSQL(
    """
      CREATE TABLE IF NOT EXISTS video_providers (
        id TEXT PRIMARY KEY NOT NULL,
        provider_type TEXT NOT NULL UNIQUE,
        name TEXT NOT NULL,
        enabled INTEGER NOT NULL DEFAULT 1,
        function_code TEXT,
        created_at INTEGER NOT NULL,
        updated_at INTEGER NOT NULL
      )
    """.trimIndent(),
  )
  ensureVideoProviderFunctionCodeColumn(db)
  db.execSQL(
    """
      CREATE TABLE IF NOT EXISTS video_subscriptions (
        id TEXT PRIMARY KEY NOT NULL,
        provider_id TEXT NOT NULL,
        source_id TEXT NOT NULL,
        title TEXT NOT NULL,
        source_url TEXT NOT NULL,
        created_at INTEGER NOT NULL,
        updated_at INTEGER NOT NULL,
        UNIQUE(provider_id, source_id),
        FOREIGN KEY(provider_id) REFERENCES video_providers(id) ON DELETE CASCADE
      )
    """.trimIndent(),
  )
  db.execSQL(
    """
      CREATE TABLE IF NOT EXISTS video_provider_items (
        video_id TEXT PRIMARY KEY NOT NULL,
        provider_id TEXT NOT NULL,
        subscription_id TEXT,
        provider_item_id TEXT NOT NULL,
        published_at INTEGER NOT NULL,
        is_read INTEGER NOT NULL DEFAULT 0,
        is_watch_later INTEGER NOT NULL DEFAULT 0,
        UNIQUE(provider_id, provider_item_id),
        FOREIGN KEY(video_id) REFERENCES video_items(id) ON DELETE CASCADE,
        FOREIGN KEY(provider_id) REFERENCES video_providers(id) ON DELETE CASCADE,
        FOREIGN KEY(subscription_id) REFERENCES video_subscriptions(id) ON DELETE SET NULL
      )
    """.trimIndent(),
  )
  db.execSQL(
    "CREATE INDEX IF NOT EXISTS idx_video_items_source_updated ON video_items(source, updated_at DESC)",
  )
  db.execSQL(
    "CREATE INDEX IF NOT EXISTS idx_video_smb_sources_updated ON video_smb_sources(updated_at DESC)",
  )
  db.execSQL(
    "CREATE INDEX IF NOT EXISTS idx_video_folders_name ON video_folders(normalized_name)",
  )
  db.execSQL(
    "CREATE INDEX IF NOT EXISTS idx_video_saved_items_folder_saved ON video_saved_items(folder_id, saved_at DESC)",
  )
  db.execSQL(
    "CREATE INDEX IF NOT EXISTS idx_video_rules_updated ON video_web_extractor_rules(updated_at DESC)",
  )
  db.execSQL(
    "CREATE INDEX IF NOT EXISTS idx_video_subscriptions_provider ON video_subscriptions(provider_id, title COLLATE NOCASE)",
  )
  db.execSQL(
    "CREATE INDEX IF NOT EXISTS idx_video_provider_items_unread ON video_provider_items(is_read, is_watch_later, published_at DESC)",
  )
  db.execSQL(
    "CREATE INDEX IF NOT EXISTS idx_video_provider_items_subscription ON video_provider_items(subscription_id, published_at DESC)",
  )
}

private fun ensureVideoWebExtractorRuleCookieColumn(db: SQLiteDatabase) {
  if (!db.hasColumn("video_web_extractor_rules", "share_cookies_for_playback")) {
    db.execSQL(
      "ALTER TABLE video_web_extractor_rules " +
        "ADD COLUMN share_cookies_for_playback INTEGER NOT NULL DEFAULT 0",
    )
  }
}

private fun ensureVideoWebExtractorRuleReferrerPathColumn(db: SQLiteDatabase) {
  if (!db.hasColumn("video_web_extractor_rules", "share_referrer_path_for_playback")) {
    db.execSQL(
      "ALTER TABLE video_web_extractor_rules " +
        "ADD COLUMN share_referrer_path_for_playback INTEGER NOT NULL DEFAULT 0",
    )
  }
}

private fun ensureVideoProviderFunctionCodeColumn(db: SQLiteDatabase) {
  if (db.tableExists("video_providers") && !db.hasColumn("video_providers", "function_code")) {
    db.execSQL("ALTER TABLE video_providers ADD COLUMN function_code TEXT")
  }
}

private fun migrateCustomVideoProviderCode(db: SQLiteDatabase) {
  ensureVideoProviderFunctionCodeColumn(db)
}

private fun SQLiteDatabase.hasColumn(table: String, column: String): Boolean = rawQuery(
  "PRAGMA table_info($table)",
  null,
).use { cursor ->
  val nameColumn = cursor.getColumnIndexOrThrow("name")
  var found = false
  while (cursor.moveToNext()) {
    if (cursor.getString(nameColumn) == column) {
      found = true
      break
    }
  }
  found
}

private fun SQLiteDatabase.tableExists(name: String): Boolean = rawQuery(
  "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ? LIMIT 1",
  arrayOf(name),
).use { it.moveToFirst() }
