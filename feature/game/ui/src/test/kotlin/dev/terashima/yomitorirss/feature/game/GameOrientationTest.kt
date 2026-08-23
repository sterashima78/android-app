package dev.terashima.yomitorirss.feature.game

import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class GameOrientationTest {
  @Test
  fun `クロンダイクとスパイダーは横向きを要求する`() {
    assertEquals(
      ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
      requestedOrientationFor(GameScreen.KLONDIKE),
    )
    assertEquals(
      ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
      requestedOrientationFor(GameScreen.SPIDER),
    )
  }

  @Test
  fun `その他のゲーム画面は縦向きを要求する`() {
    GameScreen.entries
      .filterNot { it == GameScreen.KLONDIKE || it == GameScreen.SPIDER }
      .forEach { screen ->
        assertEquals(
          "$screen should stay portrait",
          ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
          requestedOrientationFor(screen),
        )
      }
  }
}
