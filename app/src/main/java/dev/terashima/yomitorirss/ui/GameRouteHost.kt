package dev.terashima.yomitorirss.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.terashima.yomitorirss.feature.game.GameChromePreference
import dev.terashima.yomitorirss.feature.game.GameOrientationPreference
import dev.terashima.yomitorirss.feature.game.GameRoute

internal val LocalGameFullscreenChange = staticCompositionLocalOf<(Boolean) -> Unit> { {} }

@Composable
internal fun GameRouteHost(modifier: Modifier) {
  val activity = LocalContext.current.findActivity()
  val onFullscreenChange = LocalGameFullscreenChange.current
  var orientationPreference by remember { mutableStateOf<GameOrientationPreference?>(null) }
  val currentOnFullscreenChange by rememberUpdatedState(onFullscreenChange)

  orientationPreference?.let { preference ->
    LaunchedEffect(activity, preference) {
      activity?.requestedOrientation = preference.toRequestedOrientation()
    }
  }

  DisposableEffect(activity) {
    onDispose {
      if (activity?.isChangingConfigurations != true) {
        currentOnFullscreenChange(false)
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
      }
    }
  }

  GameRoute(
    modifier = modifier,
    onOrientationPreferenceChange = { orientationPreference = it },
    onChromePreferenceChange = { preference ->
      currentOnFullscreenChange(preference == GameChromePreference.FULLSCREEN)
    },
  )
}

internal fun GameOrientationPreference.toRequestedOrientation(): Int = when (this) {
  GameOrientationPreference.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
  GameOrientationPreference.SENSOR_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.findActivity()
  else -> null
}
