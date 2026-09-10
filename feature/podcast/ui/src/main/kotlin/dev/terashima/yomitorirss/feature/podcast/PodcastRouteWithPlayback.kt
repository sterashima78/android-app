package dev.terashima.yomitorirss.feature.podcast

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun PodcastRouteWithPlayback(
  viewModelFactory: PodcastViewModel.Factory,
  modifier: Modifier = Modifier,
) {
  val viewModel: PodcastViewModel = viewModel(factory = viewModelFactory)
  val state by viewModel.state.collectAsStateWithLifecycle()
  val audioState by viewModel.audioState.collectAsStateWithLifecycle()

  PodcastRoute(
    viewModelFactory = viewModelFactory,
    modifier = modifier,
  )

  state.playbackEpisode?.let { episode ->
    PodcastPlaybackDialog(
      episode = episode,
      audioState = audioState,
      onTogglePlayPause = viewModel::togglePlayPause,
      onPrevious = viewModel::skipPrevious,
      onNext = viewModel::skipNext,
      onSeekBack = viewModel::seekBack,
      onSeekForward = viewModel::seekForward,
      onSetSpeed = viewModel::setPlaybackSpeed,
      onStop = viewModel::stopPlayback,
      onDismiss = viewModel::dismissPlayback,
    )
  }
}
