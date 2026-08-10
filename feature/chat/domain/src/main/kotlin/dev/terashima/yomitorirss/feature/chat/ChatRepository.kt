package dev.terashima.yomitorirss.feature.chat

interface ChatRepository {
  suspend fun listSessions(): List<ChatSession>
  suspend fun createSession(title: String): ChatSession
  suspend fun listMessages(sessionId: String): List<StoredChatMessage>
  suspend fun appendMessage(sessionId: String, role: ChatRole, content: String): StoredChatMessage
}
