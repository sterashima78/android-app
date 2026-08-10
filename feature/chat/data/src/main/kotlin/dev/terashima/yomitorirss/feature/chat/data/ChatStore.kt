package dev.terashima.yomitorirss.feature.chat.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dev.terashima.yomitorirss.feature.chat.ChatRole
import dev.terashima.yomitorirss.feature.chat.ChatSession
import dev.terashima.yomitorirss.feature.chat.StoredChatMessage
import java.time.Instant
import java.util.UUID

internal class ChatStore(context: Context) : SQLiteOpenHelper(
  context.applicationContext,
  DB_NAME,
  null,
  DB_VERSION,
) {
  override fun onConfigure(db: SQLiteDatabase) {
    super.onConfigure(db)
    db.setForeignKeyConstraintsEnabled(true)
  }

  override fun onCreate(db: SQLiteDatabase) {
    db.execSQL(
      "CREATE TABLE chat_sessions(" +
        "id TEXT PRIMARY KEY NOT NULL," +
        "title TEXT NOT NULL," +
        "created_at TEXT NOT NULL," +
        "updated_at TEXT NOT NULL)",
    )
    db.execSQL(
      "CREATE TABLE chat_messages(" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
        "session_id TEXT NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE," +
        "role TEXT NOT NULL," +
        "content TEXT NOT NULL," +
        "created_at TEXT NOT NULL)",
    )
    db.execSQL("CREATE INDEX chat_messages_session ON chat_messages(session_id,id)")
    db.execSQL("CREATE INDEX chat_sessions_updated ON chat_sessions(updated_at DESC)")
  }

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

  fun listSessions(): List<ChatSession> = readableDatabase.rawQuery(
    "SELECT id,title,created_at,updated_at FROM chat_sessions " +
      "ORDER BY updated_at DESC,created_at DESC LIMIT $MAX_SESSIONS",
    null,
  ).use { cursor ->
    buildList {
      while (cursor.moveToNext()) {
        add(
          ChatSession(
            id = cursor.getString(0),
            title = cursor.getString(1),
            createdAt = cursor.getString(2),
            updatedAt = cursor.getString(3),
          ),
        )
      }
    }
  }

  fun createSession(title: String): ChatSession {
    val timestamp = Instant.now().toString()
    val session = ChatSession(
      id = UUID.randomUUID().toString(),
      title = normalizeTitle(title),
      createdAt = timestamp,
      updatedAt = timestamp,
    )
    writableDatabase.beginTransaction()
    try {
      writableDatabase.insertOrThrow(
        "chat_sessions",
        null,
        ContentValues().apply {
          put("id", session.id)
          put("title", session.title)
          put("created_at", session.createdAt)
          put("updated_at", session.updatedAt)
        },
      )
      pruneOldSessions(writableDatabase)
      writableDatabase.setTransactionSuccessful()
    } finally {
      writableDatabase.endTransaction()
    }
    return session
  }

  fun listMessages(sessionId: String): List<StoredChatMessage> = readableDatabase.rawQuery(
    "SELECT id,session_id,role,content,created_at FROM chat_messages WHERE session_id=? ORDER BY id ASC",
    arrayOf(sessionId),
  ).use { cursor ->
    buildList {
      while (cursor.moveToNext()) {
        add(
          StoredChatMessage(
            id = cursor.getLong(0),
            sessionId = cursor.getString(1),
            role = ChatRole.valueOf(cursor.getString(2)),
            content = cursor.getString(3),
            createdAt = cursor.getString(4),
          ),
        )
      }
    }
  }

  fun appendMessage(sessionId: String, role: ChatRole, content: String): StoredChatMessage {
    val normalized = content.trim()
    require(normalized.isNotBlank()) { "メッセージを入力してください" }
    val timestamp = Instant.now().toString()
    val database = writableDatabase
    database.beginTransaction()
    return try {
      val id = database.insertOrThrow(
        "chat_messages",
        null,
        ContentValues().apply {
          put("session_id", sessionId)
          put("role", role.name)
          put("content", normalized)
          put("created_at", timestamp)
        },
      )
      check(id >= 0) { "チャットメッセージを保存できませんでした" }
      val updatedRows = database.update(
        "chat_sessions",
        ContentValues().apply { put("updated_at", timestamp) },
        "id=?",
        arrayOf(sessionId),
      )
      check(updatedRows == 1) { "チャットセッションが見つかりません" }
      pruneOldSessions(database)
      database.setTransactionSuccessful()
      StoredChatMessage(id, sessionId, role, normalized, timestamp)
    } finally {
      database.endTransaction()
    }
  }

  private fun pruneOldSessions(db: SQLiteDatabase) {
    db.execSQL(
      "DELETE FROM chat_sessions WHERE id IN (" +
        "SELECT id FROM chat_sessions " +
        "ORDER BY updated_at DESC,created_at DESC LIMIT -1 OFFSET $MAX_SESSIONS)",
    )
  }

  private fun normalizeTitle(value: String): String {
    val normalized = value.replace(Regex("\\s+"), " ").trim()
    if (normalized.isBlank()) return "新しいチャット"
    return if (normalized.length <= TITLE_MAX_CHARS) normalized else normalized.take(TITLE_MAX_CHARS) + "…"
  }

  companion object {
    private const val MAX_SESSIONS = 5
    private const val DB_NAME = "yomitori-chat.db"
    private const val DB_VERSION = 1
    private const val TITLE_MAX_CHARS = 28
  }
}
