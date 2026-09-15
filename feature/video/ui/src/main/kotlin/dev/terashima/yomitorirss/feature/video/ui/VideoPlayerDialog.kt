package dev.terashima.yomitorirss.feature.video.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.Player
import dev.terashima.yomitorirss.feature.video.VideoByteSourceFactory
import dev.terashima.yomitorirss.feature.video.VideoItem
import dev.terashima.yomitorirss.feature.video.VideoPlaybackTarget

@Composable
internal fun VideoPlayerDialog(
  item: VideoItem,
  target: VideoPlaybackTarget,
  resumePositionMs: Long,
  isFullscreen: Boolean,
  byteSourceFactory: VideoByteSourceFactory,
  onFullscreenChange: (Boolean) -> Unit,
  onSavePlayback: (positionMs: Long, durationMs: Long) -> Unit,
  onDismiss: () -> Unit,
) {
  require(target is VideoPlaybackTarget.Stream || target is VideoPlaybackTarget.Smb)
  val context = LocalContext.current
  val media = remember(item.id, target, resumePositionMs, byteSourceFactory) {
    createVideoPlayerMedia(
      context = context,
      target = target,
      resumePositionMs = resumePositionMs,
      byteSourceFactory = byteSourceFactory,
    )
  }
  val player = media.player
  var playbackState by remember(player) { mutableStateOf(player.playbackState) }
  var playbackErrorCodeName by remember(player) { mutableStateOf<String?>(null) }
  var playbackHttpStatusCode by remember(player) { mutableStateOf<Int?>(null) }
  var loadingElapsedMs by remember(player) { mutableStateOf(0L) }

  VideoPlayerLifecycleEffects(
    item = item,
    media = media,
    playbackState = playbackState,
    playbackErrorCodeName = playbackErrorCodeName,
    onPlaybackStateChanged = { playbackState = it },
    onPlaybackErrorChanged = { errorCodeName, httpStatusCode ->
      playbackErrorCodeName = errorCodeName
      playbackHttpStatusCode = httpStatusCode
    },
    onLoadingElapsedChanged = { loadingElapsedMs = it },
    onSavePlayback = onSavePlayback,
  )

  fun retryPlayback() {
    playbackErrorCodeName = null
    playbackHttpStatusCode = null
    playbackState = Player.STATE_IDLE
    loadingElapsedMs = 0L
    player.stop()
    player.prepare()
    player.playWhenReady = true
  }

  val statusUi = videoPlayerStatusUi(
    playbackState = playbackState,
    loadingElapsedMs = loadingElapsedMs,
    errorCodeName = playbackErrorCodeName,
    httpStatusCode = playbackHttpStatusCode,
  )

  Dialog(
    onDismissRequest = {
      if (isFullscreen) {
        onFullscreenChange(false)
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
        VideoPlayerView(
          player = player,
          modifier = Modifier.fillMaxSize(),
        )
        statusUi?.let { status ->
          Surface(
            modifier = Modifier
              .align(Alignment.Center)
              .padding(24.dp),
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 6.dp,
          ) {
            Column(
              modifier = Modifier.padding(20.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              if (status.showProgress) CircularProgressIndicator()
              Text(
                text = status.message,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
              )
              status.errorCodeName?.let {
                Text(
                  text = "エラーコード: $it",
                  textAlign = TextAlign.Center,
                  style = MaterialTheme.typography.bodySmall,
                )
              }
              status.httpStatusCode?.let {
                Text(
                  text = "HTTPステータス: $it",
                  textAlign = TextAlign.Center,
                  style = MaterialTheme.typography.bodySmall,
                )
              }
              if (status.httpStatusCode == 403 && target is VideoPlaybackTarget.Stream) {
                webVideoPlaybackDiagnosticLines(
                  diagnostics = target.playbackDiagnostics,
                  nativeRequestProperties = webStreamRequestProperties(target),
                ).forEach { line ->
                  Text(
                    text = line,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                  )
                }
              }
              if (status.canRetry) {
                Button(onClick = ::retryPlayback) {
                  Text("再試行")
                }
              }
            }
          }
        }
        Row(modifier = Modifier.align(Alignment.TopEnd)) {
          IconButton(onClick = { onFullscreenChange(!isFullscreen) }) {
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
