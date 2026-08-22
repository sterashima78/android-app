package dev.terashima.yomitorirss.feature.web

import org.junit.Assert.assertEquals
import org.junit.Test

class WebServerDialogTest {
  @Test
  fun `サーバ状態に応じて状態ラベルを切り替える`() {
    assertEquals("停止中", webServerStatusText(LanWebServerState()))
    assertEquals("起動中", webServerStatusText(LanWebServerState(running = true)))
  }
}
