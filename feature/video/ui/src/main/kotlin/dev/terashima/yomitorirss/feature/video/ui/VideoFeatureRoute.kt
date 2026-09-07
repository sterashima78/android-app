package dev.terashima.yomitorirss.feature.video.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.terashima.yomitorirss.feature.video.VideoByteSourceFactory
import dev.terashima.yomitorirss.feature.video.VideoItem
import dev.terashima.yomitorirss.feature.video.VideoPlaybackResolver
import dev.terashima.yomitorirss.feature.video.VideoPlaybackTarget
import kotlinx.coroutines.launch

@Composable
fun VideoFeatureRoute(
  viewModelFactory: VideoViewModel.Factory,
  playbackResolver: VideoPlaybackResolver,
  byteSourceFactory: VideoByteSourceFactory,
  onOpenWebUrl: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val viewModel: VideoViewModel = viewModel(factory = viewModelFactory)
  val state by viewModel.state.collectAsState()
  val scope = rememberCoroutineScope()
  var playerItem by remember { mutableStateOf<VideoItem?>(null) }
  var playerTarget by remember { mutableStateOf<VideoPlaybackTarget?>(null) }
  var resolving by remember { mutableStateOf(false) }

  fun play(item: VideoItem) {
    if (resolving) return
    resolving = true
    scope.launch {
      runCatching { playbackResolver.resolve(item) }.fold(
        onSuccess = { target ->
          when (target) {
            is VideoPlaybackTarget.WebPage -> onOpenWebUrl(target.url)
            is VideoPlaybackTarget.Stream,
            is VideoPlaybackTarget.Smb,
            -> {
              playerItem = item
              playerTarget = target
            }
          }
        },
        onFailure = {
          item.pageUrl?.let(onOpenWebUrl)
        },
      )
      resolving = false
    }
  }

  Box(modifier.fillMaxSize()) {
    VideoScreen(
      state = state,
      onPlay = ::play,
      onAddWeb = viewModel::addWeb,
      onRefreshSmb = viewModel::refreshSmb,
      onRemove = viewModel::remove,
      onSetCompleted = viewModel::setCompleted,
      onSaveExtractorRule = viewModel::saveExtractorRule,
      onDeleteExtractorRule = viewModel::deleteExtractorRule,
      onDismissMessage = viewModel::dismissMessage,
      modifier = Modifier.fillMaxSize(),
    )
    if (resolving) {
      CircularProgressIndicator(Modifier.align(Alignment.Center))
    }
  }

  val activeItem = playerItem
  val activeTarget = playerTarget
  if (activeItem != null && activeTarget != null) {
    VideoPlayerDialog(
      item = activeItem,
      target = activeTarget,
      byteSourceFactory = byteSourceFactory,
      onSavePlayback = { positionMs, durationMs ->
        viewModel.savePlayback(activeItem, positionMs, durationMs)
      },
      onDismiss = {
        playerItem = null
        playerTarget = null
        viewModel.reload()
      },
    )
  }
}
