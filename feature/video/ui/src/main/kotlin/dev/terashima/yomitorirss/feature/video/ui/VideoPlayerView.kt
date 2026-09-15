package dev.terashima.yomitorirss.feature.video.ui

import android.view.GestureDetector
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@Composable
internal fun VideoPlayerView(
  player: ExoPlayer,
  modifier: Modifier = Modifier,
) {
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
    modifier = modifier,
  )
}
