package dev.terashima.yomitorirss.feature.web
import dev.terashima.yomitorirss.feature.web.LanServerUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class WebServerDialogTest {
  @Test
  fun `サーバ状態に応じて状態ラベルを切り替える`() {
    assertEquals("停止中", webServerStatusText(LanServerUiState()))
    assertEquals("起動中", webServerStatusText(LanServerUiState(running = true)))
  }
}
