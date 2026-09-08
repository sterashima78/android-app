package dev.terashima.yomitorirss.feature.video.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.WindowInsets
import android.view.WindowInsetsController
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import dev.terashima.yomitorirss.feature.video.VideoByteSourceFactory
import dev.terashima.yomitorirss.feature.video.VideoItem
import dev.terashima.yomitorirss.feature.video.VideoPlaybackTarget
import java.net.URI
import kotlinx.coroutines.delay

internal const val VIDEO_PLAYER_SLOW_LOADING_MS = 10_000L
internal const val VIDEO_PLAYER_STALLED_LOADING_MS = 30_000L

internal data class VideoPlayerStatusUi(
  val message: String,
  val showProgress: Boolean,
  val canRetry: Boolean,
  val errorCodeName: String? = null,
  val httpStatusCode: Int? = null,
)

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
  val player = remember(item.id, target) {
    val builder = ExoPlayer.Builder(context)
    when (target) {
      is VideoPlaybackTarget.Stream -> {
        val httpFactory = DefaultHttpDataSource.Factory()
          .setUserAgent(WebSettings.getDefaultUserAgent(context))
          .setDefaultRequestProperties(webStreamRequestProperties(target))
        builder.setMediaSourceFactory(
          DefaultMediaSourceFactory(context).setDataSourceFactory(httpFactory),
        )
      }
      is VideoPlaybackTarget.Smb -> builder.setMediaSourceFactory(
        DefaultMediaSourceFactory(SmbVideoDataSource.Factory(byteSourceFactory)),
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

  DisposableEffect(player) {
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
    FullscreenOrientationEffect(isFullscreen)
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

internal fun videoPlayerStatusUi(
  playbackState: Int,
  loadingElapsedMs: Long,
  errorCodeName: String?,
  httpStatusCode: Int? = null,
): VideoPlayerStatusUi? {
  if (!errorCodeName.isNullOrBlank()) {
    return VideoPlayerStatusUi(
      message = "再生できません。",
      showProgress = false,
      canRetry = true,
      errorCodeName = errorCodeName,
      httpStatusCode = httpStatusCode,
    )
  }

  val isLoading = playbackState == Player.STATE_IDLE || playbackState == Player.STATE_BUFFERING
  if (!isLoading) return null

  return when {
    loadingElapsedMs >= VIDEO_PLAYER_STALLED_LOADING_MS -> VideoPlayerStatusUi(
      message = "30秒以上読み込みが続いています。再生エラーはまだ検出されていません。",
      showProgress = true,
      canRetry = true,
    )
    loadingElapsedMs >= VIDEO_PLAYER_SLOW_LOADING_MS -> VideoPlayerStatusUi(
      message = "再生開始を待っています（${loadingElapsedMs / 1_000}秒）。通常より時間がかかっています。",
      showProgress = true,
      canRetry = false,
    )
    playbackState == Player.STATE_IDLE -> VideoPlayerStatusUi(
      message = "再生を準備しています…",
      showProgress = true,
      canRetry = false,
    )
    else -> VideoPlayerStatusUi(
      message = "動画を読み込んでいます…",
      showProgress = true,
      canRetry = false,
    )
  }
}

internal fun findHttpStatusCode(error: Throwable?): Int? {
  var current = error
  while (current != null) {
    if (current is HttpDataSource.InvalidResponseCodeException) return current.responseCode
    current = current.cause
  }
  return null
}

internal fun webStreamRequestProperties(target: VideoPlaybackTarget.Stream): Map<String, String> = buildMap {
  val referrerUrl = target.referrerUrl?.takeIf(String::isNotBlank) ?: return@buildMap
  put("Referer", referrerUrl)
  webStreamOriginHeaderValue(referrerUrl)?.let { put("Origin", it) }
}

internal fun webStreamOriginHeaderValue(referrerUrl: String): String? = runCatching {
  val uri = URI(referrerUrl)
  val scheme = uri.scheme?.lowercase()
  require((scheme == "https" || scheme == "http") && !uri.host.isNullOrBlank())
  URI(scheme, null, uri.host, uri.port, null, null, null).toString()
}.getOrNull()

@Composable
private fun FullscreenOrientationEffect(isFullscreen: Boolean) {
  val activity = LocalContext.current.findActivity()

  LaunchedEffect(activity, isFullscreen) {
    activity?.requestedOrientation = videoPlayerRequestedOrientation(isFullscreen)
  }

  DisposableEffect(activity) {
    onDispose {
      if (activity?.isChangingConfigurations != true) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
      }
    }
  }
}

internal fun videoPlayerRequestedOrientation(isFullscreen: Boolean): Int = if (isFullscreen) {
  ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
} else {
  ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.findActivity()
  else -> null
}
