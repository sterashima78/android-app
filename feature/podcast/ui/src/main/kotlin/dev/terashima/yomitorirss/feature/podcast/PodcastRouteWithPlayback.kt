package dev.terashima.yomitorirss.feature.podcast

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.terashima.yomitorirss.feature.audio.AudioPlaybackState

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
        when (val distance = podcastChapterJumpDistance(audioState, episode.id, chapterNumber)) {
          null, 0 -> Unit
          else -> if (distance > 0) {
            repeat(distance) { viewModel.skipNext() }
          } else {
            repeat(-distance) { viewModel.skipPrevious() }
          }
        }
      },
      onStop = viewModel::stopPlayback,
      onDismiss = viewModel::dismissPlayback,
    )
  }
}

internal fun podcastChapterJumpDistance(
  audioState: AudioPlaybackState,
  episodeId: String,
  chapterNumber: Int,
): Int? {
  if (audioState.currentIndex !in audioState.items.indices) return null
  val targetContentId = podcastChapterContentId(episodeId, chapterNumber)
  val targetIndex = audioState.items.indexOfFirst { it.contentId == targetContentId }
  if (targetIndex < 0) return null
  return targetIndex - audioState.currentIndex
}
