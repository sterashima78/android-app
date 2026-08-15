package dev.terashima.yomitorirss.feature.knowledge.data

import dev.terashima.yomitorirss.core.database.DatabaseSchemaContribution

val knowledgeDatabaseSchema = DatabaseSchemaContribution(
  owner = "knowledge",
  createSchema = { db ->
    db.execSQL("CREATE TABLE IF NOT EXISTS knowledge_pages(id TEXT PRIMARY KEY NOT NULL,title TEXT NOT NULL,body_markdown TEXT NOT NULL,topic_kind TEXT NOT NULL,topic_key TEXT NOT NULL,source_count INTEGER NOT NULL,source_fingerprint TEXT NOT NULL,generated_at TEXT NOT NULL)")
    db.execSQL("CREATE TABLE IF NOT EXISTS knowledge_page_sources(page_id TEXT NOT NULL REFERENCES knowledge_pages(id) ON DELETE CASCADE,article_id TEXT NOT NULL,citation_index INTEGER NOT NULL,title TEXT NOT NULL,url TEXT NOT NULL,source_title TEXT NOT NULL,saved_at TEXT NOT NULL,PRIMARY KEY(page_id,article_id))")
    db.execSQL("CREATE INDEX IF NOT EXISTS knowledge_pages_title ON knowledge_pages(title)")
    db.execSQL("CREATE INDEX IF NOT EXISTS knowledge_page_sources_article ON knowledge_page_sources(article_id)")
  },
)
