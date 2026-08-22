package dev.terashima.yomitorirss.feature.web.data

import android.content.Context
import dev.terashima.yomitorirss.feature.web.LanServerUiState
import dev.terashima.yomitorirss.feature.web.LanWebServerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidLanWebServerController(
  context: Context,
) : LanWebServerController {
  private val appContext = context.applicationContext

  override val state: StateFlow<LanServerUiState> = LanWebServerStateStore.state

  override fun start() {
    LanWebServerService.start(appContext)
  }

  override fun stop() {
    LanWebServerService.stop(appContext)
  }

  override fun reportError(message: String) {
    LanWebServerStateStore.reportError(message)
  }
}

internal object LanWebServerStateStore {
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
