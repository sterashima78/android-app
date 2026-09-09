package dev.terashima.yomitorirss.feature.podcast.data

import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DatabaseMigration
import dev.terashima.yomitorirss.core.database.DatabaseSchemaContribution

val podcastDatabaseSchema = DatabaseSchemaContribution(
  owner = "podcast",
  createSchema = ::createPodcastSchema,
  migrations = listOf(
    DatabaseMigration(targetVersion = 33) { db -> createPodcastSchema(db) },
  ),
)

private fun createPodcastSchema(db: SQLiteDatabase) {
  db.execSQL(
    "CREATE TABLE IF NOT EXISTS podcast_programs(" +
      "id TEXT PRIMARY KEY NOT NULL," +
      "name TEXT NOT NULL," +
      "feed_ids TEXT NOT NULL," +
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
      "error_message TEXT" +
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
      "feed_content TEXT NOT NULL," +
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
