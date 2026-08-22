package dev.terashima.yomitorirss.feature.web.data

import android.content.Context
import dev.terashima.yomitorirss.feature.web.LanWebServerController
import dev.terashima.yomitorirss.feature.web.LanWebServerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidLanWebServerController(
  context: Context,
) : LanWebServerController {
  private val appContext = context.applicationContext

  override val state: StateFlow<LanWebServerState> = LanWebServerStateStore.state

  override fun start() {
    LanWebServerService.start(appContext)
  }

  override fun stop() {
    LanWebServerService.stop(appContext)
  }
}

internal object LanWebServerStateStore {
  private val mutableState = MutableStateFlow(LanWebServerState())
  val state: StateFlow<LanWebServerState> = mutableState.asStateFlow()

  fun starting() {
    mutableState.value = LanWebServerState(running = true)
  }

  fun running(address: String?, accessUrl: String?) {
    mutableState.value = LanWebServerState(
      running = true,
      address = address,
      accessUrl = accessUrl,
    )
  }

  fun stopped(error: String? = null) {
    mutableState.value = LanWebServerState(error = error)
  }
}
