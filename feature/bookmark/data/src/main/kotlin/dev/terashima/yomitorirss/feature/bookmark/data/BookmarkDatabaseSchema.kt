package dev.terashima.yomitorirss.feature.bookmark.data

import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DatabaseMigration
import dev.terashima.yomitorirss.core.database.DatabaseSchemaContribution

val bookmarkDatabaseSchema = DatabaseSchemaContribution(
  owner = "bookmark",
  createSchema = { db ->
    db.execSQL("CREATE TABLE IF NOT EXISTS bookmarks(article_id TEXT PRIMARY KEY NOT NULL REFERENCES articles(id) ON DELETE CASCADE,saved_at TEXT NOT NULL)")
    db.execSQL("CREATE INDEX IF NOT EXISTS bookmark_saved_date ON bookmarks(saved_at DESC,article_id)")
    db.execSQL("CREATE TABLE IF NOT EXISTS tags(id TEXT PRIMARY KEY NOT NULL,name TEXT NOT NULL,normalized_name TEXT NOT NULL UNIQUE,created_at TEXT NOT NULL)")
    db.execSQL("CREATE TABLE IF NOT EXISTS article_tags(article_id TEXT NOT NULL REFERENCES articles(id) ON DELETE CASCADE,tag_id TEXT NOT NULL REFERENCES tags(id) ON DELETE CASCADE,PRIMARY KEY(article_id,tag_id))")
    db.execSQL("CREATE TABLE IF NOT EXISTS bookmark_folders(id TEXT PRIMARY KEY NOT NULL,name TEXT NOT NULL,normalized_name TEXT NOT NULL UNIQUE,system_kind TEXT,created_at TEXT NOT NULL)")
    db.execSQL("CREATE TABLE IF NOT EXISTS article_folders(article_id TEXT PRIMARY KEY NOT NULL REFERENCES articles(id) ON DELETE CASCADE,folder_id TEXT NOT NULL REFERENCES bookmark_folders(id) ON DELETE CASCADE)")
    db.execSQL("CREATE INDEX IF NOT EXISTS article_folder_folder_id ON article_folders(folder_id,article_id)")
    createUnusedTagCleanupTrigger(db)
  },
  migrations = listOf(
    DatabaseMigration(targetVersion = 13) { db ->
      db.execSQL(
        "DELETE FROM tags WHERE NOT EXISTS(SELECT 1 FROM article_tags WHERE article_tags.tag_id=tags.id)",
      )
    },
    DatabaseMigration(targetVersion = 25) { db ->
      if (db.columnExists("articles", "saved_at")) {
        db.execSQL(
          "INSERT OR IGNORE INTO bookmarks(article_id,saved_at) SELECT id,saved_at FROM articles WHERE saved_at IS NOT NULL",
        )
      }
    },
  ),
)

private fun createUnusedTagCleanupTrigger(db: SQLiteDatabase) {
  db.execSQL(
    """
      CREATE TRIGGER IF NOT EXISTS cleanup_unused_tags_after_article_tag_delete
      AFTER DELETE ON article_tags
      WHEN NOT EXISTS(SELECT 1 FROM article_tags WHERE tag_id=OLD.tag_id)
      BEGIN
        DELETE FROM tags WHERE id=OLD.tag_id;
      END
    """.trimIndent(),
  )
}

private fun SQLiteDatabase.columnExists(table: String, column: String): Boolean =
  rawQuery("PRAGMA table_info($table)", null).use { cursor ->
    var found = false
    while (cursor.moveToNext()) {
      if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == column) {
        found = true
        break
      }
    }
    found
  }
