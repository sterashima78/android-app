package dev.terashima.yomitorirss.feature.web

import kotlinx.coroutines.flow.StateFlow

data class LanServerUiState(
  val running: Boolean = false,
  val address: String? = null,
  val port: Int = 8765,
  val accessUrl: String? = null,
  val error: String? = null,
)

interface LanWebServerController {
  val state: StateFlow<LanServerUiState>

  fun start()

  fun stop()

  fun reportError(message: String)
}
