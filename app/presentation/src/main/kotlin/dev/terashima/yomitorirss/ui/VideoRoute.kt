package dev.terashima.yomitorirss.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
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
  VideoFeatureRoute(
    viewModelFactory = dependencies.viewModelFactory,
    playbackResolver = dependencies.playbackResolver,
    byteSourceFactory = dependencies.byteSourceFactory,
    onOpenWebUrl = { url ->
      if (!onOpenWebContent(url)) {
        Toast.makeText(context, "Web動画ページを開けませんでした", Toast.LENGTH_LONG).show()
      }
    },
    modifier = modifier,
  )
}
