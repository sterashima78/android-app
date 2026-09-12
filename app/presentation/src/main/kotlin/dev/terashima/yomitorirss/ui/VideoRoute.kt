package dev.terashima.yomitorirss.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.terashima.yomitorirss.composition.route.VideoRouteDependencies
import dev.terashima.yomitorirss.feature.video.ui.VideoFeatureRoute

@Composable
internal fun VideoRoute(
  dependencies: VideoRouteDependencies,
  onOpenWebContent: (String) -> Boolean,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val activity = context.findActivity()
  var fullscreenPresentationRequested by remember { mutableStateOf(false) }

  LaunchedEffect(activity, fullscreenPresentationRequested) {
    activity?.requestedOrientation = videoRequestedOrientation(fullscreenPresentationRequested)
  }

  DisposableEffect(activity) {
    onDispose {
      if (activity?.isChangingConfigurations != true) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
      }
    }
  }

  VideoFeatureRoute(
    viewModelFactory = dependencies.viewModelFactory,
    playbackResolver = dependencies.playbackResolver,
    byteSourceFactory = dependencies.byteSourceFactory,
    onOpenWebUrl = { url ->
      if (!onOpenWebContent(url)) {
        Toast.makeText(context, "Web動画ページを開けませんでした", Toast.LENGTH_LONG).show()
      }
    },
    onPlaybackFullscreenPresentationChange = { fullscreenPresentationRequested = it },
    modifier = modifier,
  )
}

internal fun videoRequestedOrientation(isFullscreen: Boolean): Int = if (isFullscreen) {
  ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
} else {
  ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.findActivity()
  else -> null
}
