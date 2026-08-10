package dev.terashima.yomitorirss.feature.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatUiStateTest {
  @Test
  fun `初期状態はセッション未選択で送信していない`() {
    val state = ChatUiState()

    assertFalse(state.initialized)
    assertTrue(state.sessions.isEmpty())
    assertNull(state.activeSessionId)
    assertTrue(state.messages.isEmpty())
    assertTrue(state.streamingReply.isEmpty())
    assertFalse(state.sending)
    assertFalse(state.responseStarted)
    assertNull(state.errorText)
  }
}
