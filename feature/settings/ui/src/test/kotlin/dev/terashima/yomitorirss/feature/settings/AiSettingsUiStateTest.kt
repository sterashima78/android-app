package dev.terashima.yomitorirss.feature.settings

import dev.terashima.yomitorirss.feature.knowledge.KnowledgeExecutionProvider
import dev.terashima.yomitorirss.feature.summary.SummaryExecutionProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSettingsUiStateTest {
  @Test
  fun `初期状態はモデル未読込で進捗を持たない`() {
    val state = AiSettingsUiState()

    assertFalse(state.supported)
    assertTrue(state.models.isEmpty())
    assertNull(state.downloadProgress)
    assertNull(state.summaryProgress)
    assertTrue(state.summaryPrompt.isEmpty())
    assertEquals(KnowledgeExecutionProvider.LOCAL, state.knowledgeExecutionProvider)
    assertNull(state.message)
  }

  @Test
  fun `ChatGPT logout returns cloud execution settings to local`() {
    assertEquals(
      SummaryExecutionProvider.LOCAL,
      summaryExecutionProviderAfterChatGptLogout(SummaryExecutionProvider.CHATGPT),
    )
    assertEquals(
      SummaryExecutionProvider.LOCAL,
      summaryExecutionProviderAfterChatGptLogout(SummaryExecutionProvider.LOCAL),
    )
    assertEquals(
      KnowledgeExecutionProvider.LOCAL,
      knowledgeExecutionProviderAfterChatGptLogout(KnowledgeExecutionProvider.CHATGPT),
    )
    assertEquals(
      KnowledgeExecutionProvider.LOCAL,
      knowledgeExecutionProviderAfterChatGptLogout(KnowledgeExecutionProvider.LOCAL),
    )
  }
}
