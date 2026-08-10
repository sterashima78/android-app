package dev.terashima.yomitorirss.feature.settings

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
    assertNull(state.message)
  }
}
