package dev.terashima.yomitorirss.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.terashima.yomitorirss.feature.youtube.YouTubeRoute as FeatureYouTubeRoute
import dev.terashima.yomitorirss.feature.youtube.YouTubeViewModel

@Composable
internal fun YouTubeRouteHost(
  viewModelFactory: YouTubeViewModel.Factory,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  FeatureYouTubeRoute(
    viewModelFactory = viewModelFactory,
    onOpen = { video ->
      runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(video.url)))
      }
    },
    modifier = modifier,
  )
}
