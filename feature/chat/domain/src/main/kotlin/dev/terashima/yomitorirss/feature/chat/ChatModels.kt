package dev.terashima.yomitorirss.feature.chat

enum class ChatRole {
  USER,
  ASSISTANT,
}

data class ChatSession(
  val id: String,
  val title: String,
  val createdAt: String,
  val updatedAt: String,
)

data class StoredChatMessage(
  val id: Long,
  val sessionId: String,
  val role: ChatRole,
  val content: String,
  val createdAt: String,
)

data class ChatTurn(
  val role: ChatRole,
  val content: String,
)

data class ChatModelStatus(
  val id: String,
  val name: String,
)

data class ChatProgress(
  val stage: String,
  val modelName: String? = null,
  val estimatedStageDurationMillis: Long? = null,
)

data class ChatContextBlock(
  val sourceId: String,
  val label: String,
  val content: String,
)
