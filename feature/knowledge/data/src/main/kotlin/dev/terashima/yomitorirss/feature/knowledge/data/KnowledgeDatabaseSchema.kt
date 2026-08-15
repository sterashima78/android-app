package dev.terashima.yomitorirss.feature.knowledge.data

import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DatabaseMigration
import dev.terashima.yomitorirss.core.database.DatabaseSchemaContribution

val knowledgeDatabaseSchema = DatabaseSchemaContribution(
  owner = "knowledge",
  createSchema = { db ->
    db.execSQL("CREATE TABLE IF NOT EXISTS knowledge_pages(id TEXT PRIMARY KEY NOT NULL,title TEXT NOT NULL,body_markdown TEXT NOT NULL,topic_kind TEXT NOT NULL,topic_key TEXT NOT NULL,source_count INTEGER NOT NULL,source_fingerprint TEXT NOT NULL,generated_at TEXT NOT NULL,editor_managed INTEGER NOT NULL DEFAULT 0)")
    db.execSQL("CREATE TABLE IF NOT EXISTS knowledge_page_sources(page_id TEXT NOT NULL REFERENCES knowledge_pages(id) ON DELETE CASCADE,article_id TEXT NOT NULL,citation_index INTEGER NOT NULL,title TEXT NOT NULL,url TEXT NOT NULL,source_title TEXT NOT NULL,saved_at TEXT NOT NULL,PRIMARY KEY(page_id,article_id))")
    db.execSQL("CREATE INDEX IF NOT EXISTS knowledge_pages_title ON knowledge_pages(title)")
    db.execSQL("CREATE INDEX IF NOT EXISTS knowledge_page_sources_article ON knowledge_page_sources(article_id)")
  },
  migrations = listOf(
    DatabaseMigration(targetVersion = 15) { db -> ensureEditorManagedColumn(db) },
  ),
)

private fun ensureEditorManagedColumn(db: SQLiteDatabase) {
  val hasColumn = db.rawQuery("PRAGMA table_info(knowledge_pages)", emptyArray()).use { cursor ->
    val nameIndex = cursor.getColumnIndexOrThrow("name")
    var found = false
    while (cursor.moveToNext()) {
      if (cursor.getString(nameIndex) == "editor_managed") {
        found = true
        break
      }
    }
    found
  }
  if (!hasColumn) {
    db.execSQL("ALTER TABLE knowledge_pages ADD COLUMN editor_managed INTEGER NOT NULL DEFAULT 0")
  }
}
