package dev.terashima.yomitorirss.feature.summary.data

import dev.terashima.yomitorirss.core.database.DatabaseMigration
import dev.terashima.yomitorirss.core.database.DatabaseSchemaContribution
import dev.terashima.yomitorirss.core.database.addColumnIfMissing

val summaryDatabaseSchema = DatabaseSchemaContribution(
  owner = "summary",
  createSchema = { db ->
    db.execSQL("CREATE TABLE IF NOT EXISTS article_summaries(article_id TEXT PRIMARY KEY NOT NULL REFERENCES articles(id) ON DELETE CASCADE,summary TEXT NOT NULL,model_id TEXT NOT NULL,created_at TEXT NOT NULL)")
    db.execSQL("CREATE TABLE IF NOT EXISTS summary_tasks(article_id TEXT PRIMARY KEY NOT NULL REFERENCES articles(id) ON DELETE CASCADE,state TEXT NOT NULL,force_refresh INTEGER NOT NULL DEFAULT 0,queued_at TEXT NOT NULL,started_at TEXT,finished_at TEXT,error TEXT,progress_stage TEXT,progress_current INTEGER,progress_total INTEGER)")
    db.execSQL("CREATE INDEX IF NOT EXISTS summary_task_state ON summary_tasks(state,queued_at)")
  },
  migrations = listOf(
    DatabaseMigration(targetVersion = 12) { db ->
      db.addColumnIfMissing("summary_tasks", "progress_stage", "progress_stage TEXT")
      db.addColumnIfMissing("summary_tasks", "progress_current", "progress_current INTEGER")
      db.addColumnIfMissing("summary_tasks", "progress_total", "progress_total INTEGER")
    },
  ),
)
