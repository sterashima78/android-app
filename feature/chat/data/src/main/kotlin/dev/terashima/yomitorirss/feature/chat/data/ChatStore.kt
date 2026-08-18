package dev.terashima.yomitorirss.feature.chat.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.chat.ChatRole
import dev.terashima.yomitorirss.feature.chat.ChatSession
import dev.terashima.yomitorirss.feature.chat.StoredChatMessage
import java.time.Instant
import java.util.UUID

internal class ChatStore(
  private val database: DatabaseConnection,
) {
  fun listSessions(): List<ChatSession> = database.readable.rawQuery(
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
    database.transaction {
      insertOrThrow(
        "chat_sessions",
        null,
        ContentValues().apply {
          put("id", session.id)
          put("title", session.title)
          put("created_at", session.createdAt)
          put("updated_at", session.updatedAt)
        },
      )
      pruneOldSessions(this)
    }
    return session
  }

  fun listMessages(sessionId: String): List<StoredChatMessage> = database.readable.rawQuery(
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
    return database.transaction {
      val id = insertOrThrow(
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
      val updatedRows = update(
        "chat_sessions",
        ContentValues().apply { put("updated_at", timestamp) },
        "id=?",
        arrayOf(sessionId),
      )
      check(updatedRows == 1) { "チャットセッションが見つかりません" }
      pruneOldSessions(this)
      StoredChatMessage(id, sessionId, role, normalized, timestamp)
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
    private const val TITLE_MAX_CHARS = 28
  }
}
