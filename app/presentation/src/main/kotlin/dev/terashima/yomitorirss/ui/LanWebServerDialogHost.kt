package dev.terashima.yomitorirss.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
  val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions(),
  ) {
    val error = webServerPermissionError(context)
    if (error == null) {
      permissionError = null
      controller.start()
    } else {
      permissionError = error
    }
  }

  WebServerDialog(
    state = serverState.copy(
      error = permissionError ?: serverState.error,
    ),
    onDismiss = onDismiss,
    onStart = {
      permissionError = null
      val missingPermissions = webServerMissingPermissions(context)
      if (missingPermissions.isEmpty()) {
        controller.start()
      } else {
        permissionLauncher.launch(missingPermissions.toTypedArray())
      }
    },
    onStop = {
      permissionError = null
      controller.stop()
    },
  )
}

private fun webServerMissingPermissions(context: Context): List<String> = buildList {
  if (
    ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.POST_NOTIFICATIONS,
    ) != PackageManager.PERMISSION_GRANTED
  ) {
    add(Manifest.permission.POST_NOTIFICATIONS)
  }
  if (
    Build.VERSION.SDK_INT >= 37 &&
    ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.ACCESS_LOCAL_NETWORK,
    ) != PackageManager.PERMISSION_GRANTED
  ) {
    add(Manifest.permission.ACCESS_LOCAL_NETWORK)
  }
}

private fun webServerPermissionError(context: Context): String? {
  val missingPermissions = webServerMissingPermissions(context)
  return when {
    Manifest.permission.ACCESS_LOCAL_NETWORK in missingPermissions ->
      "Webサーバの起動にはローカルネットワークへのアクセス許可が必要です。"
    Manifest.permission.POST_NOTIFICATIONS in missingPermissions ->
      "通知を許可しないとWebサーバを起動できません。"
    else -> null
  }
}
