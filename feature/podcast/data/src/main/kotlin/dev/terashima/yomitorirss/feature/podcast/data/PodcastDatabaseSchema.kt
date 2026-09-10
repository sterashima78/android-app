package dev.terashima.yomitorirss.feature.podcast.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DatabaseMigration
import dev.terashima.yomitorirss.core.database.DatabaseSchemaContribution

val podcastDatabaseSchema = DatabaseSchemaContribution(
  owner = "podcast",
  createSchema = ::createPodcastSchema,
  migrations = listOf(
    DatabaseMigration(targetVersion = 33) { db -> createPodcastSchema(db) },
    DatabaseMigration(targetVersion = 34) { db -> migratePodcastSources(db) },
    DatabaseMigration(targetVersion = 35) { db -> migratePodcastArticleUrls(db) },
    DatabaseMigration(targetVersion = 36) { db -> migratePodcastChapterGeneration(db) },
  ),
)

private fun createPodcastSchema(db: SQLiteDatabase) {
  createPodcastSourcesTable(db)
  db.execSQL(
    "CREATE TABLE IF NOT EXISTS podcast_programs(" +
      "id TEXT PRIMARY KEY NOT NULL," +
      "name TEXT NOT NULL," +
      "source_ids TEXT NOT NULL," +
      "provider TEXT NOT NULL," +
      "schedule_enabled INTEGER NOT NULL DEFAULT 0," +
      "schedule_hour INTEGER NOT NULL DEFAULT 7," +
      "schedule_minute INTEGER NOT NULL DEFAULT 0," +
      "max_articles INTEGER NOT NULL DEFAULT 12" +
      ")",
  )
  db.execSQL(
    "CREATE TABLE IF NOT EXISTS podcast_episodes(" +
      "id TEXT PRIMARY KEY NOT NULL," +
      "program_id TEXT NOT NULL REFERENCES podcast_programs(id) ON DELETE CASCADE," +
      "title TEXT NOT NULL," +
      "created_at INTEGER NOT NULL," +
      "status TEXT NOT NULL," +
      "script TEXT," +
      "error_message TEXT," +
      "regeneration_status TEXT" +
      ")",
  )
  db.execSQL("CREATE INDEX IF NOT EXISTS podcast_episodes_program_created ON podcast_episodes(program_id,created_at DESC)")
  db.execSQL(
    "CREATE TABLE IF NOT EXISTS podcast_episode_articles(" +
      "episode_id TEXT NOT NULL REFERENCES podcast_episodes(id) ON DELETE CASCADE," +
      "position INTEGER NOT NULL," +
      "article_id TEXT NOT NULL," +
      "feed_id TEXT NOT NULL," +
      "title TEXT NOT NULL," +
      "source_title TEXT," +
      "published_at INTEGER," +
      "article_url TEXT," +
      "feed_content TEXT NOT NULL," +
      "chapter_status TEXT NOT NULL DEFAULT 'PENDING'," +
      "chapter_script TEXT," +
      "chapter_error TEXT," +
      "PRIMARY KEY(episode_id,position)" +
      ")",
  )
  db.execSQL(
    "CREATE TABLE IF NOT EXISTS podcast_consumed_articles(" +
      "program_id TEXT NOT NULL REFERENCES podcast_programs(id) ON DELETE CASCADE," +
      "article_id TEXT NOT NULL," +
      "episode_id TEXT NOT NULL REFERENCES podcast_episodes(id) ON DELETE CASCADE," +
      "consumed_at INTEGER NOT NULL," +
      "PRIMARY KEY(program_id,article_id)" +
      ")",
  )
}

private fun createPodcastSourcesTable(db: SQLiteDatabase) {
  db.execSQL(
    "CREATE TABLE IF NOT EXISTS podcast_sources(" +
      "id TEXT PRIMARY KEY NOT NULL," +
      "name TEXT NOT NULL," +
      "feed_url TEXT NOT NULL UNIQUE" +
      ")",
  )
}

private fun migratePodcastSources(db: SQLiteDatabase) {
  createPodcastSourcesTable(db)
  if (!db.hasColumn("podcast_programs", "feed_ids")) return

  val legacySourceIds = db.rawQuery("SELECT feed_ids FROM podcast_programs", null).use { cursor ->
    buildSet {
      while (cursor.moveToNext()) {
        cursor.getString(0).lineSequence().filter(String::isNotBlank).forEach(::add)
      }
    }
  }
  legacySourceIds.forEach { sourceId ->
    db.rawQuery(
      "SELECT COALESCE(custom_title,title),feed_url FROM feeds WHERE id=? LIMIT 1",
      arrayOf(sourceId),
    ).use { cursor ->
      if (!cursor.moveToFirst()) return@use
      db.insertWithOnConflict(
        "podcast_sources",
        null,
        ContentValues().apply {
          put("id", sourceId)
          put("name", cursor.getString(0))
          put("feed_url", cursor.getString(1))
        },
        SQLiteDatabase.CONFLICT_IGNORE,
      )
    }
  }

  migrateLegacyConsumedIdentities(db)
  db.execSQL("ALTER TABLE podcast_programs RENAME COLUMN feed_ids TO source_ids")
}

private fun migratePodcastArticleUrls(db: SQLiteDatabase) {
  if (!db.hasColumn("podcast_episode_articles", "article_url")) {
    db.execSQL("ALTER TABLE podcast_episode_articles ADD COLUMN article_url TEXT")
  }
}

private fun migratePodcastChapterGeneration(db: SQLiteDatabase) {
  if (db.hasTable("podcast_episodes") && !db.hasColumn("podcast_episodes", "regeneration_status")) {
    db.execSQL("ALTER TABLE podcast_episodes ADD COLUMN regeneration_status TEXT")
  }
  if (!db.hasTable("podcast_episode_articles")) return
  if (!db.hasColumn("podcast_episode_articles", "chapter_status")) {
    db.execSQL("ALTER TABLE podcast_episode_articles ADD COLUMN chapter_status TEXT NOT NULL DEFAULT 'PENDING'")
  }
  if (!db.hasColumn("podcast_episode_articles", "chapter_script")) {
    db.execSQL("ALTER TABLE podcast_episode_articles ADD COLUMN chapter_script TEXT")
  }
  if (!db.hasColumn("podcast_episode_articles", "chapter_error")) {
    db.execSQL("ALTER TABLE podcast_episode_articles ADD COLUMN chapter_error TEXT")
  }
  if (db.hasTable("podcast_episodes")) {
    db.execSQL(
      "UPDATE podcast_episode_articles SET chapter_status='READY' " +
        "WHERE episode_id IN (SELECT id FROM podcast_episodes WHERE status='READY')",
    )
  }
}

private fun migrateLegacyConsumedIdentities(db: SQLiteDatabase) {
  val mappings = db.rawQuery(
    "SELECT id,feed_id,identity_key FROM articles " +
      "WHERE feed_id IS NOT NULL AND id IN (SELECT article_id FROM podcast_consumed_articles)",
    null,
  ).use { cursor ->
    buildList {
      while (cursor.moveToNext()) {
        add(LegacyArticleIdentity(cursor.getString(0), cursor.getString(1), cursor.getString(2)))
      }
    }
  }
  mappings.forEach { mapping ->
    val migratedId = "${mapping.sourceId}:${mapping.identityKey}"
    db.execSQL(
      "UPDATE podcast_episode_articles SET article_id=? WHERE article_id=? AND feed_id=?",
      arrayOf(migratedId, mapping.articleId, mapping.sourceId),
    )
    db.execSQL(
      "UPDATE podcast_consumed_articles SET article_id=? WHERE article_id=?",
      arrayOf(migratedId, mapping.articleId),
    )
  }
}

private fun SQLiteDatabase.hasColumn(table: String, column: String): Boolean =
  rawQuery("PRAGMA table_info($table)", null).use { cursor ->
    val nameIndex = cursor.getColumnIndexOrThrow("name")
    while (cursor.moveToNext()) {
      if (cursor.getString(nameIndex) == column) return@use true
    }
    false
  }

private fun SQLiteDatabase.hasTable(table: String): Boolean =
  rawQuery(
    "SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1",
    arrayOf(table),
  ).use { cursor -> cursor.moveToFirst() }

private data class LegacyArticleIdentity(
  val articleId: String,
  val sourceId: String,
  val identityKey: String,
)
