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
      onSelectChapter = { chapterNumber ->
        val targetContentId = podcastChapterContentId(episode.id, chapterNumber)
        val targetIndex = audioState.items.indexOfFirst { it.contentId == targetContentId }
        val currentIndex = audioState.currentIndex
        if (targetIndex >= 0 && currentIndex >= 0) {
          when {
            targetIndex > currentIndex -> repeat(targetIndex - currentIndex) { viewModel.skipNext() }
            targetIndex < currentIndex -> repeat(currentIndex - targetIndex) { viewModel.skipPrevious() }
          }
        }
      },
      onStop = viewModel::stopPlayback,
      onDismiss = viewModel::dismissPlayback,
    )
  }
}
