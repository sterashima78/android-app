package dev.terashima.yomitorirss.feature.web
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LanServerUiState(
  val running: Boolean = false,
  val address: String? = null,
  val port: Int = 8765,
  val accessUrl: String? = null,
  val error: String? = null,
)

object LanServerStatus {
  private val mutableState = MutableStateFlow(LanServerUiState())
  val state: StateFlow<LanServerUiState> = mutableState.asStateFlow()

  fun starting() {
    mutableState.value = LanServerUiState(running = true)
  }

  fun running(address: String?, accessUrl: String?) {
    mutableState.value = LanServerUiState(
      running = true,
      address = address,
      accessUrl = accessUrl,
    )
  }

  fun stopped(error: String? = null) {
    mutableState.value = LanServerUiState(error = error)
  }

  fun reportError(message: String) {
    mutableState.value = mutableState.value.copy(error = message)
  }
}
