package dev.terashima.yomitorirss.feature.video.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
  val playbackSession by viewModel.playbackSession.collectAsState()
  val scope = rememberCoroutineScope()
  var resolving by remember { mutableStateOf(false) }
  var smbSettingsVisible by remember { mutableStateOf(false) }
  var providerSettingsVisible by remember { mutableStateOf(false) }

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
            -> viewModel.openPlayback(item, target)
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
      onRefreshSmb = { smbSettingsVisible = true },
      onRemove = viewModel::remove,
      onSetCompleted = viewModel::setCompleted,
      onSaveVideo = viewModel::saveVideo,
      onRemoveSavedVideo = viewModel::removeSavedVideo,
      onSaveFolder = viewModel::saveFolder,
      onDeleteFolder = viewModel::deleteFolder,
      onSaveExtractorRule = viewModel::saveExtractorRule,
      onDeleteExtractorRule = viewModel::deleteExtractorRule,
      onDismissMessage = viewModel::dismissMessage,
      modifier = Modifier.fillMaxSize(),
    )
    Button(
      onClick = { providerSettingsVisible = true },
      enabled = !state.busy,
      modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(12.dp),
    ) {
      Text("購読設定")
    }
    if (resolving) {
      CircularProgressIndicator(Modifier.align(Alignment.Center))
    }
    state.busyMessage?.let { busyMessage ->
      Card(
        modifier = Modifier
          .align(Alignment.TopCenter)
          .padding(16.dp),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          CircularProgressIndicator(Modifier.padding(end = 12.dp), strokeWidth = 2.dp)
          Text(busyMessage)
        }
      }
    }
  }

  if (smbSettingsVisible) {
    VideoSmbSettingsDialog(
      state = state,
      onSave = viewModel::saveSmbSource,
      onDelete = viewModel::deleteSmbSource,
      onSync = viewModel::refreshSmb,
      onDismiss = { smbSettingsVisible = false },
    )
  }

  if (providerSettingsVisible) {
    VideoProviderSettingsDialog(
      state = state,
      onSaveProvider = viewModel::saveProvider,
      onDeleteProvider = viewModel::deleteProvider,
      onSubscribe = viewModel::subscribe,
      onUnsubscribe = viewModel::unsubscribe,
      onRefresh = viewModel::refreshProviders,
      onMarkRead = viewModel::markProviderRead,
      onDismiss = { providerSettingsVisible = false },
    )
  }

  val activeSession = playbackSession
  if (activeSession != null) {
    VideoPlayerDialog(
      item = activeSession.item,
      target = activeSession.target,
      resumePositionMs = viewModel.resumePositionMs(activeSession.item),
      isFullscreen = activeSession.isFullscreen,
      byteSourceFactory = byteSourceFactory,
      onFullscreenChange = viewModel::setPlaybackFullscreen,
      onSavePlayback = { positionMs, durationMs ->
        viewModel.savePlayback(activeSession.item, positionMs, durationMs)
      },
      onDismiss = {
        viewModel.closePlayback()
        viewModel.reload()
      },
    )
  }
}
