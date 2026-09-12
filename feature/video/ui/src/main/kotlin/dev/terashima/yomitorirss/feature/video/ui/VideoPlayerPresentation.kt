package dev.terashima.yomitorirss.feature.video.ui

import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

internal const val VIDEO_PLAYER_DOUBLE_TAP_SEEK_MS = 15_000L

internal fun videoPlayerDoubleTapSeekPositionMs(
  currentPositionMs: Long,
  durationMs: Long,
  tapX: Float,
  playerWidth: Float,
): Long {
  val current = currentPositionMs.coerceAtLeast(0L)
  if (playerWidth <= 0f) return current
  val deltaMs = if (tapX < playerWidth / 2f) {
    -VIDEO_PLAYER_DOUBLE_TAP_SEEK_MS
  } else {
    VIDEO_PLAYER_DOUBLE_TAP_SEEK_MS
  }
  val target = (current + deltaMs).coerceAtLeast(0L)
  return if (durationMs > 0L) target.coerceAtMost(durationMs) else target
}

@Composable
internal fun FullscreenSystemBarsEffect(isFullscreen: Boolean) {
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
