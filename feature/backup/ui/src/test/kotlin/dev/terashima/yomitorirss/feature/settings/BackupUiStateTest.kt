package dev.terashima.yomitorirss.feature.backup
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class BackupUiStateTest {
  @Test
  fun `初期状態は未設定かつ処理停止中になる`() {
    val state = BackupUiState()

    assertFalse(state.configured)
    assertFalse(state.running)
    assertFalse(state.restoreCompleted)
    assertNull(state.folderUri)
    assertNull(state.lastError)
    assertNull(state.message)
  }
}
