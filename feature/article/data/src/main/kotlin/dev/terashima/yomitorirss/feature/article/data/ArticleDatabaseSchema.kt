package dev.terashima.yomitorirss.feature.article.data

import dev.terashima.yomitorirss.core.database.DatabaseSchemaContribution

val articleDatabaseSchema = DatabaseSchemaContribution(
  owner = "article",
  createSchema = { db ->
    db.execSQL("CREATE TABLE IF NOT EXISTS articles(id TEXT PRIMARY KEY NOT NULL,feed_id TEXT REFERENCES feeds(id) ON DELETE SET NULL,external_id TEXT,identity_key TEXT NOT NULL,url TEXT NOT NULL,title TEXT NOT NULL,published_at TEXT NOT NULL,fetched_at TEXT NOT NULL,read_at TEXT,saved_at TEXT,source_title TEXT NOT NULL,source_feed_url TEXT NOT NULL)")
    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS article_feed_identity ON articles(feed_id,identity_key) WHERE feed_id IS NOT NULL")
    db.execSQL("CREATE INDEX IF NOT EXISTS article_unread_date ON articles(read_at,published_at DESC)")
    db.execSQL("CREATE INDEX IF NOT EXISTS article_saved_date ON articles(saved_at,published_at DESC)")
    db.execSQL("CREATE INDEX IF NOT EXISTS article_read_date ON articles(read_at DESC)")
    db.execSQL("CREATE INDEX IF NOT EXISTS article_url ON articles(url)")
  },
)
