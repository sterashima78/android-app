package dev.terashima.yomitorirss.feature.video.ui

import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import dev.terashima.yomitorirss.feature.video.VideoByteSourceFactory
import dev.terashima.yomitorirss.feature.video.VideoItem
import dev.terashima.yomitorirss.feature.video.VideoPlaybackTarget
import kotlinx.coroutines.delay

@Composable
internal fun VideoPlayerDialog(
  item: VideoItem,
  target: VideoPlaybackTarget,
  byteSourceFactory: VideoByteSourceFactory,
  onSavePlayback: (positionMs: Long, durationMs: Long) -> Unit,
  onDismiss: () -> Unit,
) {
  require(target is VideoPlaybackTarget.Stream || target is VideoPlaybackTarget.Smb)
  val context = LocalContext.current
  var isFullscreen by rememberSaveable(item.id) { mutableStateOf(false) }
  val player = remember(item.id, target) {
    val builder = ExoPlayer.Builder(context)
    if (target is VideoPlaybackTarget.Smb) {
      builder.setMediaSourceFactory(
        DefaultMediaSourceFactory(SmbVideoDataSource.Factory(byteSourceFactory)),
      )
    }
    builder.build().apply {
      val mediaItem = when (target) {
        is VideoPlaybackTarget.Stream -> MediaItem.Builder()
          .setUri(target.url)
          .apply { target.mimeType?.let(::setMimeType) }
          .build()
        is VideoPlaybackTarget.Smb -> MediaItem.Builder()
          .setUri(SmbVideoDataSource.mediaUri(target.sourceId))
          .apply { target.mimeType?.let(::setMimeType) }
          .build()
        is VideoPlaybackTarget.WebPage -> error("WebページはMedia3で再生しません")
      }
      setMediaItem(mediaItem)
      prepare()
      val resumePosition = item.playbackState?.positionMs ?: 0L
      if (resumePosition > 0L) seekTo(resumePosition)
      playWhenReady = true
    }
  }

  fun savePosition() {
    val duration = player.duration.takeIf { it > 0L } ?: item.playbackState?.durationMs ?: 0L
    onSavePlayback(player.currentPosition.coerceAtLeast(0L), duration)
  }

  DisposableEffect(player) {
    val listener = object : Player.Listener {
      override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) savePosition()
      }
    }
    player.addListener(listener)
    onDispose {
      savePosition()
      player.removeListener(listener)
      player.release()
    }
  }

  LaunchedEffect(player) {
    while (true) {
      delay(2_000)
      if (player.playbackState != Player.STATE_IDLE) savePosition()
    }
  }

  Dialog(
    onDismissRequest = {
      if (isFullscreen) {
        isFullscreen = false
      } else {
        onDismiss()
      }
    },
    properties = DialogProperties(
      usePlatformDefaultWidth = false,
      dismissOnClickOutside = false,
    ),
  ) {
    FullscreenSystemBarsEffect(isFullscreen)
    Surface(
      modifier = Modifier.fillMaxSize(),
      color = if (isFullscreen) Color.Black else MaterialTheme.colorScheme.surface,
    ) {
      Box(Modifier.fillMaxSize()) {
        AndroidView(
          factory = { viewContext ->
            PlayerView(viewContext).apply {
              useController = true
              this.player = player
            }
          },
          update = { it.player = player },
          modifier = Modifier.fillMaxSize(),
        )
        Row(modifier = Modifier.align(Alignment.TopEnd)) {
          IconButton(onClick = { isFullscreen = !isFullscreen }) {
            Icon(
              imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
              contentDescription = if (isFullscreen) "全画面を終了" else "全画面表示",
            )
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "閉じる")
          }
        }
      }
    }
  }
}

@Composable
private fun FullscreenSystemBarsEffect(isFullscreen: Boolean) {
  val view = LocalView.current
  DisposableEffect(view, isFullscreen) {
    val parent = view.parent
    val window = if (parent is DialogWindowProvider) parent.window else null
    val controller = window?.insetsController
    if (isFullscreen) {
      controller?.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      controller?.hide(WindowInsets.Type.systemBars())
    } else {
      controller?.show(WindowInsets.Type.systemBars())
    }

    onDispose {
      if (isFullscreen) controller?.show(WindowInsets.Type.systemBars())
    }
  }
}
