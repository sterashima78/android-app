package dev.terashima.yomitorirss.feature.rss.data

import dev.terashima.yomitorirss.core.database.DatabaseSchemaContribution
import dev.terashima.yomitorirss.core.database.DatabaseMigration

val rssDatabaseSchema = DatabaseSchemaContribution(
  owner = "rss",
  createSchema = { db ->
    db.execSQL("CREATE TABLE IF NOT EXISTS feed_folders(id TEXT PRIMARY KEY NOT NULL,name TEXT NOT NULL,normalized_name TEXT NOT NULL UNIQUE,created_at TEXT NOT NULL,content_type TEXT)")
    db.execSQL("CREATE TABLE IF NOT EXISTS feeds(id TEXT PRIMARY KEY NOT NULL,title TEXT NOT NULL,custom_title TEXT,feed_url TEXT NOT NULL UNIQUE,site_url TEXT,etag TEXT,last_modified TEXT,last_fetched_at TEXT,last_error TEXT,created_at TEXT NOT NULL,folder_id TEXT REFERENCES feed_folders(id) ON DELETE SET NULL,content_type TEXT,refresh_interval_minutes INTEGER)")
    db.execSQL("CREATE INDEX IF NOT EXISTS feeds_folder_id ON feeds(folder_id,title)")
    ensureRssWebScrapingRuleSchema(db)
    ensureRssRecommendationSchema(db)
  },
  migrations = listOf(
    DatabaseMigration(targetVersion = 43) { db ->
      db.execSQL("ALTER TABLE feeds ADD COLUMN refresh_interval_minutes INTEGER")
      db.execSQL("ALTER TABLE rss_web_scraping_rules ADD COLUMN refresh_interval_minutes INTEGER")
    },
  ),
)
