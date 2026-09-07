package dev.terashima.yomitorirss.feature.video.ui

import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoPlayerOrientationTest {
  @Test
  fun `全画面中は横向きで通常表示では縦向きを要求する`() {
    assertEquals(
      ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
      videoPlayerRequestedOrientation(isFullscreen = true),
    )
    assertEquals(
      ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
      videoPlayerRequestedOrientation(isFullscreen = false),
    )
  }
}
