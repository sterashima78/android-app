package dev.terashima.yomitorirss.feature.bookmark.data

import dev.terashima.yomitorirss.core.database.DatabaseSchemaContribution

val bookmarkDatabaseSchema = DatabaseSchemaContribution(
  owner = "bookmark",
  createSchema = { db ->
    db.execSQL("CREATE TABLE IF NOT EXISTS tags(id TEXT PRIMARY KEY NOT NULL,name TEXT NOT NULL,normalized_name TEXT NOT NULL UNIQUE,created_at TEXT NOT NULL)")
    db.execSQL("CREATE TABLE IF NOT EXISTS article_tags(article_id TEXT NOT NULL REFERENCES articles(id) ON DELETE CASCADE,tag_id TEXT NOT NULL REFERENCES tags(id) ON DELETE CASCADE,PRIMARY KEY(article_id,tag_id))")
    db.execSQL("CREATE TABLE IF NOT EXISTS bookmark_folders(id TEXT PRIMARY KEY NOT NULL,name TEXT NOT NULL,normalized_name TEXT NOT NULL UNIQUE,system_kind TEXT,created_at TEXT NOT NULL)")
    db.execSQL("CREATE TABLE IF NOT EXISTS article_folders(article_id TEXT PRIMARY KEY NOT NULL REFERENCES articles(id) ON DELETE CASCADE,folder_id TEXT NOT NULL REFERENCES bookmark_folders(id) ON DELETE CASCADE)")
    db.execSQL("CREATE INDEX IF NOT EXISTS article_folder_folder_id ON article_folders(folder_id,article_id)")
  },
)
