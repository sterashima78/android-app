package dev.terashima.yomitorirss.feature.chat.data

import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.chat.ChatRepository
import dev.terashima.yomitorirss.feature.chat.ChatRole
import dev.terashima.yomitorirss.feature.chat.ChatSession
import dev.terashima.yomitorirss.feature.chat.StoredChatMessage

class DefaultChatRepository(
  database: DatabaseConnection,
) : ChatRepository {
  private val store = ChatStore(database)

  override suspend fun listSessions(): List<ChatSession> = store.listSessions()

  override suspend fun createSession(title: String): ChatSession = store.createSession(title)

  override suspend fun listMessages(sessionId: String): List<StoredChatMessage> = store.listMessages(sessionId)

  override suspend fun appendMessage(
    sessionId: String,
    role: ChatRole,
    content: String,
  ): StoredChatMessage = store.appendMessage(sessionId, role, content)
}
