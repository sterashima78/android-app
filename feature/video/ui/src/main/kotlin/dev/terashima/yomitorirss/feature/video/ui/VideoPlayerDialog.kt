package dev.terashima.yomitorirss.feature.video.ui

import android.view.GestureDetector
import android.view.MotionEvent
import android.webkit.WebSettings
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
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
  resumePositionMs: Long,
  isFullscreen: Boolean,
  byteSourceFactory: VideoByteSourceFactory,
  onFullscreenChange: (Boolean) -> Unit,
  onSavePlayback: (positionMs: Long, durationMs: Long) -> Unit,
  onDismiss: () -> Unit,
) {
  require(target is VideoPlaybackTarget.Stream || target is VideoPlaybackTarget.Smb)
  val context = LocalContext.current
  val smbDataSourceFactory = remember(item.id, target, byteSourceFactory) {
    if (target is VideoPlaybackTarget.Smb) {
      SmbVideoDataSource.Factory(byteSourceFactory)
    } else {
      null
    }
  }
  val player = remember(item.id, target, smbDataSourceFactory) {
    val builder = ExoPlayer.Builder(context)
    when (target) {
      is VideoPlaybackTarget.Stream -> {
        val userAgent = WebSettings.getDefaultUserAgent(context)
        val requestProperties = webStreamRequestProperties(target)
        val dataSourceFactory = target.cookieProvider?.let { cookieProvider ->
          WebVideoHttpDataSource.Factory(
            userAgent = userAgent,
            defaultRequestProperties = requestProperties,
            cookieProvider = cookieProvider,
          )
        } ?: DefaultHttpDataSource.Factory()
          .setUserAgent(userAgent)
          .setDefaultRequestProperties(requestProperties)
        builder.setMediaSourceFactory(
          DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory),
        )
      }
      is VideoPlaybackTarget.Smb -> builder.setMediaSourceFactory(
        DefaultMediaSourceFactory(requireNotNull(smbDataSourceFactory)),
      )
      is VideoPlaybackTarget.WebPage -> Unit
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
      if (resumePositionMs > 0L) seekTo(resumePositionMs)
      playWhenReady = true
    }
  }
  var playbackState by remember(player) { mutableStateOf(player.playbackState) }
  var playbackErrorCodeName by remember(player) { mutableStateOf<String?>(null) }
  var playbackHttpStatusCode by remember(player) { mutableStateOf<Int?>(null) }
  var loadingElapsedMs by remember(player) { mutableStateOf(0L) }

  fun savePosition() {
    val duration = player.duration.takeIf { it > 0L } ?: item.playbackState?.durationMs ?: 0L
    onSavePlayback(player.currentPosition.coerceAtLeast(0L), duration)
  }

  fun retryPlayback() {
    playbackErrorCodeName = null
    playbackHttpStatusCode = null
    playbackState = Player.STATE_IDLE
    loadingElapsedMs = 0L
    player.stop()
    player.prepare()
    player.playWhenReady = true
  }

  DisposableEffect(player, smbDataSourceFactory) {
    val listener = object : Player.Listener {
      override fun onPlaybackStateChanged(newPlaybackState: Int) {
        playbackState = newPlaybackState
        if (newPlaybackState == Player.STATE_READY) {
          playbackErrorCodeName = null
          playbackHttpStatusCode = null
        }
        if (newPlaybackState == Player.STATE_ENDED) savePosition()
      }

      override fun onPlayerError(error: PlaybackException) {
        playbackErrorCodeName = error.errorCodeName
        playbackHttpStatusCode = findHttpStatusCode(error)
      }
    }
    player.addListener(listener)
    onDispose {
      savePosition()
      player.removeListener(listener)
      player.release()
      smbDataSourceFactory?.close()
    }
  }

  LaunchedEffect(player, playbackState, playbackErrorCodeName) {
    loadingElapsedMs = 0L
    val isLoading = playbackState == Player.STATE_IDLE || playbackState == Player.STATE_BUFFERING
    if (isLoading && playbackErrorCodeName == null) {
      while (true) {
        delay(1_000)
        loadingElapsedMs += 1_000L
      }
    }
  }

  LaunchedEffect(player) {
    while (true) {
      delay(2_000)
      if (player.playbackState != Player.STATE_IDLE) savePosition()
    }
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
        AndroidView(
          factory = { viewContext ->
            lateinit var playerView: PlayerView
            val gestureDetector = GestureDetector(
              viewContext,
              object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean = playerView.performClick()

                override fun onDoubleTap(e: MotionEvent): Boolean {
                  playerView.hideController()
                  player.seekTo(
                    videoPlayerDoubleTapSeekPositionMs(
                      currentPositionMs = player.currentPosition,
                      durationMs = player.duration,
                      tapX = e.x,
                      playerWidth = playerView.width.toFloat(),
                    ),
                  )
                  return true
                }
              },
            )
            playerView = object : PlayerView(viewContext) {
              override fun onTouchEvent(event: MotionEvent): Boolean = gestureDetector.onTouchEvent(event)
            }.apply {
              useController = true
              keepScreenOn = true
              this.player = player
            }
            playerView
          },
          update = { it.player = player },
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
