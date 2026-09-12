package dev.terashima.yomitorirss.ui

import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoRouteOrientationTest {
  @Test
  fun `全画面要求をapp presentationのorientationへ変換する`() {
    assertEquals(
      ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
      videoRequestedOrientation(isFullscreen = true),
    )
    assertEquals(
      ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
      videoRequestedOrientation(isFullscreen = false),
    )
  }
}
