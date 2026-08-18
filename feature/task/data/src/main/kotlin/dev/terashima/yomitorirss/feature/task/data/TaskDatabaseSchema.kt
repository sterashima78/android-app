package dev.terashima.yomitorirss.feature.task.data

import dev.terashima.yomitorirss.core.database.DatabaseSchemaContribution

val taskDatabaseSchema = DatabaseSchemaContribution(
  owner = "task",
  createSchema = { db ->
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS tasks(" +
        "id TEXT PRIMARY KEY NOT NULL," +
        "title TEXT NOT NULL," +
        "description TEXT NOT NULL DEFAULT ''," +
        "parent_id TEXT REFERENCES tasks(id) ON DELETE CASCADE," +
        "due_date TEXT," +
        "completed_at TEXT," +
        "created_at TEXT NOT NULL," +
        "sort_order INTEGER NOT NULL" +
        ")",
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS task_parent_order ON tasks(parent_id,sort_order,created_at)")
    db.execSQL("CREATE INDEX IF NOT EXISTS task_due_date ON tasks(completed_at,due_date)")
  },
)
