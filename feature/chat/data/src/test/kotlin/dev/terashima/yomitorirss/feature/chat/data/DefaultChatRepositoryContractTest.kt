package dev.terashima.yomitorirss.feature.chat.data

import dev.terashima.yomitorirss.feature.chat.ChatRepository
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultChatRepositoryContractTest {
  @Test
  fun `実装はChatRepository契約を満たす`() {
    assertTrue(ChatRepository::class.java.isAssignableFrom(DefaultChatRepository::class.java))
  }
}
