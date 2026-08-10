package dev.terashima.yomitorirss.feature.chat

import kotlinx.coroutines.flow.Flow

interface ChatGenerator {
  val selectedModel: Flow<ChatModelStatus?>
  val progress: Flow<ChatProgress?>
  val streamingReply: Flow<String>

  suspend fun reply(turns: List<ChatTurn>): String
}

interface ChatContextProvider {
  suspend fun contextFor(query: String): List<ChatContextBlock>
}
