package dev.terashima.yomitorirss.ui

import android.content.pm.ActivityInfo
import dev.terashima.yomitorirss.feature.game.GameOrientationPreference
import org.junit.Assert.assertEquals
import org.junit.Test

class GameRouteHostTest {
  @Test
  fun `ゲーム画面向きの意図をAndroidの向き要求へ変換する`() {
    assertEquals(
      ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
      GameOrientationPreference.PORTRAIT.toRequestedOrientation(),
    )
    assertEquals(
      ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
      GameOrientationPreference.SENSOR_LANDSCAPE.toRequestedOrientation(),
    )
  }
}
