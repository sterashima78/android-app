package dev.terashima.yomitorirss.feature.chat.data

import dev.terashima.yomitorirss.core.database.DatabaseSchemaContribution

val chatDatabaseSchema = DatabaseSchemaContribution(
  owner = "chat",
  createSchema = { db ->
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS chat_sessions(" +
        "id TEXT PRIMARY KEY NOT NULL," +
        "title TEXT NOT NULL," +
        "created_at TEXT NOT NULL," +
        "updated_at TEXT NOT NULL)",
    )
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS chat_messages(" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
        "session_id TEXT NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE," +
        "role TEXT NOT NULL," +
        "content TEXT NOT NULL," +
        "created_at TEXT NOT NULL)",
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS chat_messages_session ON chat_messages(session_id,id)")
    db.execSQL("CREATE INDEX IF NOT EXISTS chat_sessions_updated ON chat_sessions(updated_at DESC)")
  },
)
