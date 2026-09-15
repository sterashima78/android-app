package dev.terashima.yomitorirss.feature.video.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import dev.terashima.yomitorirss.feature.video.VideoItem
import kotlinx.coroutines.delay

@Composable
internal fun VideoPlayerLifecycleEffects(
  item: VideoItem,
  media: VideoPlayerMedia,
  playbackState: Int,
  playbackErrorCodeName: String?,
  onPlaybackStateChanged: (Int) -> Unit,
  onPlaybackErrorChanged: (errorCodeName: String?, httpStatusCode: Int?) -> Unit,
  onLoadingElapsedChanged: (Long) -> Unit,
  onSavePlayback: (positionMs: Long, durationMs: Long) -> Unit,
) {
  val player = media.player

  fun savePosition() {
    val duration = player.duration.takeIf { it > 0L } ?: item.playbackState?.durationMs ?: 0L
    onSavePlayback(player.currentPosition.coerceAtLeast(0L), duration)
  }

  DisposableEffect(player, media.smbDataSourceFactory) {
    val listener = object : Player.Listener {
      override fun onPlaybackStateChanged(newPlaybackState: Int) {
        onPlaybackStateChanged(newPlaybackState)
        if (newPlaybackState == Player.STATE_READY) {
          onPlaybackErrorChanged(null, null)
        }
        if (newPlaybackState == Player.STATE_ENDED) savePosition()
      }

      override fun onPlayerError(error: PlaybackException) {
        onPlaybackErrorChanged(error.errorCodeName, findHttpStatusCode(error))
      }
    }
    player.addListener(listener)
    onDispose {
      savePosition()
      player.removeListener(listener)
      player.release()
      media.smbDataSourceFactory?.close()
    }
  }

  LaunchedEffect(player, playbackState, playbackErrorCodeName) {
    onLoadingElapsedChanged(0L)
    val isLoading = playbackState == Player.STATE_IDLE || playbackState == Player.STATE_BUFFERING
    if (isLoading && playbackErrorCodeName == null) {
      var elapsedMs = 0L
      while (true) {
        delay(1_000)
        elapsedMs += 1_000L
        onLoadingElapsedChanged(elapsedMs)
      }
    }
  }

  LaunchedEffect(player) {
    while (true) {
      delay(2_000)
      if (player.playbackState != Player.STATE_IDLE) savePosition()
    }
  }
}
