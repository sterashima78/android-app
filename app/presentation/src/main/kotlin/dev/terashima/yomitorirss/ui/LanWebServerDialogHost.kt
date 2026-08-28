package dev.terashima.yomitorirss.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import dev.terashima.yomitorirss.feature.web.LanWebServerController
import dev.terashima.yomitorirss.feature.web.WebServerDialog

@Composable
fun LanWebServerDialogHost(
  visible: Boolean,
  controller: LanWebServerController,
  onDismiss: () -> Unit,
) {
  if (!visible) return

  val context = LocalContext.current
  var permissionError by remember { mutableStateOf<String?>(null) }
  val serverState by controller.state.collectAsState()
  val notificationPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission(),
  ) { granted ->
    if (granted) {
      permissionError = null
      controller.start()
    } else {
      permissionError = "通知を許可しないとWebサーバを起動できません。"
    }
  }

  WebServerDialog(
    state = serverState.copy(
      error = permissionError ?: serverState.error,
    ),
    onDismiss = onDismiss,
    onStart = {
      permissionError = null
      if (
        ContextCompat.checkSelfPermission(
          context,
          Manifest.permission.POST_NOTIFICATIONS,
        ) != PackageManager.PERMISSION_GRANTED
      ) {
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
      } else {
        controller.start()
      }
    },
    onStop = {
      permissionError = null
      controller.stop()
    },
  )
}
