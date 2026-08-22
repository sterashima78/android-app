package dev.terashima.yomitorirss.feature.library.data

import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DatabaseSchemaContribution

val libraryDatabaseSchema = DatabaseSchemaContribution(
  owner = "library",
  createSchema = ::ensureLibrarySchema,
)

internal fun ensureLibrarySchema(db: SQLiteDatabase) {
  ensureLibraryCatalogSchema(db)
  db.execSQL(
    """
      CREATE TABLE IF NOT EXISTS smb_library_servers(
        id TEXT PRIMARY KEY NOT NULL,
        name TEXT NOT NULL,
        host TEXT NOT NULL,
        port INTEGER NOT NULL,
        share_name TEXT NOT NULL,
        root_path TEXT NOT NULL,
        username TEXT NOT NULL,
        domain_name TEXT NOT NULL,
        updated_at INTEGER NOT NULL
      )
    """.trimIndent(),
  )
  db.execSQL(
    """
      CREATE TABLE IF NOT EXISTS smb_cover_prefetch_queue(
        source_id TEXT PRIMARY KEY NOT NULL,
        title TEXT NOT NULL,
        status TEXT NOT NULL,
        downloaded_bytes INTEGER NOT NULL DEFAULT 0,
        total_bytes INTEGER NOT NULL DEFAULT 0,
        message TEXT,
        updated_at INTEGER NOT NULL
      )
    """.trimIndent(),
  )
  db.execSQL(
    "CREATE INDEX IF NOT EXISTS idx_smb_cover_prefetch_status ON smb_cover_prefetch_queue(status, updated_at)",
  )
  ensureLibraryOrganizationSchema(db)
  ensureSmbMetadataNormalizationSchema(db)
}

internal fun ensureLibraryCatalogSchema(db: SQLiteDatabase) {
  db.execSQL(
    """
      CREATE TABLE IF NOT EXISTS library_items(
        source TEXT NOT NULL,
        source_id TEXT NOT NULL,
        title TEXT NOT NULL,
        authors TEXT NOT NULL,
        publisher TEXT,
        published_date TEXT,
        description TEXT,
        isbn10 TEXT,
        isbn13 TEXT,
        thumbnail_url TEXT,
        info_url TEXT,
        narrators TEXT NOT NULL DEFAULT '[]',
        duration TEXT,
        synced_at INTEGER NOT NULL,
        PRIMARY KEY(source, source_id)
      )
    """.trimIndent(),
  )
  db.execSQL(
    "CREATE INDEX IF NOT EXISTS library_items_source_title " +
      "ON library_items(source, title COLLATE NOCASE)",
  )
  db.execSQL(
    """
      CREATE TABLE IF NOT EXISTS library_sources(
        source TEXT PRIMARY KEY NOT NULL,
        account_label TEXT,
        last_synced_at INTEGER
      )
    """.trimIndent(),
  )
  db.execSQL(
    """
      CREATE TABLE IF NOT EXISTS hidden_library_items(
        source TEXT NOT NULL,
        source_id TEXT NOT NULL,
        hidden_at INTEGER NOT NULL,
        PRIMARY KEY(source, source_id)
      )
    """.trimIndent(),
  )
  db.execSQL(
    """
      CREATE TABLE IF NOT EXISTS library_item_series(
        source TEXT NOT NULL,
        source_id TEXT NOT NULL,
        series_name TEXT NOT NULL,
        series_position INTEGER,
        updated_at INTEGER NOT NULL,
        PRIMARY KEY(source, source_id)
      )
    """.trimIndent(),
  )
  db.execSQL(
    "CREATE INDEX IF NOT EXISTS library_item_series_name " +
      "ON library_item_series(series_name COLLATE NOCASE)",
  )
  db.execSQL(
    """
      CREATE TABLE IF NOT EXISTS library_item_series_exclusions(
        source TEXT NOT NULL,
        source_id TEXT NOT NULL,
        updated_at INTEGER NOT NULL,
        PRIMARY KEY(source, source_id)
      )
    """.trimIndent(),
  )
}
