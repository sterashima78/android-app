package dev.terashima.yomitorirss.feature.podcast.data

import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DatabaseMigration
import dev.terashima.yomitorirss.core.database.DatabaseSchemaContribution

val podcastDatabaseSchema = DatabaseSchemaContribution(
  owner = "podcast",
  createSchema = ::createPodcastSchema,
  migrations = listOf(
    DatabaseMigration(targetVersion = 39, migrate = ::migratePodcastTo39),
    DatabaseMigration(targetVersion = 40, migrate = ::migratePodcastTo40),
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
      "max_articles INTEGER NOT NULL DEFAULT 12," +
      "exclusion_prompt TEXT NOT NULL DEFAULT ''," +
      "clustering_mode TEXT NOT NULL DEFAULT 'EVENT'" +
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
      "regeneration_status TEXT," +
      "clustering_status TEXT," +
      "clustering_mode TEXT NOT NULL DEFAULT 'EVENT'" +
      ")",
  )
  db.execSQL(
    "CREATE INDEX IF NOT EXISTS podcast_episodes_program_created " +
      "ON podcast_episodes(program_id,created_at DESC)",
  )
  db.execSQL(
    "CREATE TABLE IF NOT EXISTS podcast_episode_articles(" +
      "episode_id TEXT NOT NULL REFERENCES podcast_episodes(id) ON DELETE CASCADE," +
      "position INTEGER NOT NULL," +
      "chapter_position INTEGER," +
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
  createPodcastExcludedArticlesTable(db)
}

private fun createPodcastExcludedArticlesTable(db: SQLiteDatabase) {
  db.execSQL(
    "CREATE TABLE IF NOT EXISTS podcast_excluded_articles(" +
      "program_id TEXT NOT NULL REFERENCES podcast_programs(id) ON DELETE CASCADE," +
      "article_id TEXT NOT NULL," +
      "excluded_at INTEGER NOT NULL," +
      "PRIMARY KEY(program_id,article_id)" +
      ")",
  )
}

private fun migratePodcastTo39(db: SQLiteDatabase) {
  db.execSQL("ALTER TABLE podcast_programs ADD COLUMN exclusion_prompt TEXT NOT NULL DEFAULT ''")
  createPodcastExcludedArticlesTable(db)
}

private fun migratePodcastTo40(db: SQLiteDatabase) {
  listOf("podcast_programs", "podcast_episodes").forEach { table ->
    val exists = db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
      val nameIndex = cursor.getColumnIndexOrThrow("name")
      var found = false
      while (cursor.moveToNext()) {
        if (cursor.getString(nameIndex) == "clustering_mode") found = true
      }
      found
    }
    if (!exists) {
      db.execSQL("ALTER TABLE $table ADD COLUMN clustering_mode TEXT NOT NULL DEFAULT 'EVENT'")
    }
  }
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
