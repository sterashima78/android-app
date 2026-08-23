package dev.terashima.yomitorirss.feature.game

import org.junit.Assert.assertEquals
import org.junit.Test

class GameOrientationTest {
  @Test
  fun `クロンダイクとスパイダーは横向きを要求する`() {
    assertEquals(
      GameOrientationPreference.SENSOR_LANDSCAPE,
      orientationPreferenceFor(GameScreen.KLONDIKE),
    )
    assertEquals(
      GameOrientationPreference.SENSOR_LANDSCAPE,
      orientationPreferenceFor(GameScreen.SPIDER),
    )
  }

  @Test
  fun `その他のゲーム画面は縦向きを要求する`() {
    GameScreen.entries
      .filterNot { it == GameScreen.KLONDIKE || it == GameScreen.SPIDER }
      .forEach { screen ->
        assertEquals(
          "$screen should stay portrait",
          GameOrientationPreference.PORTRAIT,
          orientationPreferenceFor(screen),
        )
      }
  }
}
