package dev.terashima.yomitorirss.feature.web

import kotlinx.coroutines.flow.StateFlow

data class LanWebServerState(
  val running: Boolean = false,
  val address: String? = null,
  val port: Int = 8765,
  val accessUrl: String? = null,
  val error: String? = null,
)

interface LanWebServerController {
  val state: StateFlow<LanWebServerState>

  fun start()

  fun stop()
}
