package dev.terashima.yomitorirss.feature.rss.data

import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DatabaseMigration
import dev.terashima.yomitorirss.core.database.DatabaseSchemaContribution

val rssDatabaseSchema = DatabaseSchemaContribution(
  owner = "rss",
  createSchema = { db ->
    db.execSQL("CREATE TABLE IF NOT EXISTS feed_folders(id TEXT PRIMARY KEY NOT NULL,name TEXT NOT NULL,normalized_name TEXT NOT NULL UNIQUE,created_at TEXT NOT NULL,content_type TEXT)")
    db.execSQL("CREATE TABLE IF NOT EXISTS feeds(id TEXT PRIMARY KEY NOT NULL,title TEXT NOT NULL,feed_url TEXT NOT NULL UNIQUE,site_url TEXT,etag TEXT,last_modified TEXT,last_fetched_at TEXT,last_error TEXT,created_at TEXT NOT NULL,folder_id TEXT REFERENCES feed_folders(id) ON DELETE SET NULL,content_type TEXT)")
    db.execSQL("CREATE INDEX IF NOT EXISTS feeds_folder_id ON feeds(folder_id,title)")
  },
  migrations = listOf(
    DatabaseMigration(targetVersion = 19) { db ->
      db.addColumnIfMissing("feed_folders", "content_type", "content_type TEXT")
      db.addColumnIfMissing("feeds", "content_type", "content_type TEXT")
    },
  ),
)

private fun SQLiteDatabase.addColumnIfMissing(table: String, column: String, definition: String) {
  val exists = rawQuery("PRAGMA table_info($table)", null).use { cursor ->
    var found = false
    while (cursor.moveToNext()) {
      if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == column) {
        found = true
        break
      }
    }
    found
  }
  if (!exists) execSQL("ALTER TABLE $table ADD COLUMN $definition")
}
