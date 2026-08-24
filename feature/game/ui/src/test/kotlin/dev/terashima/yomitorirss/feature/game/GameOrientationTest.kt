package dev.terashima.yomitorirss.feature.game

import org.junit.Assert.assertEquals
import org.junit.Test

class GameOrientationTest {
  @Test
  fun `クロンダイクとスパイダーは横向きかつ全画面を要求する`() {
    listOf(GameScreen.KLONDIKE, GameScreen.SPIDER).forEach { screen ->
      assertEquals(
        GameOrientationPreference.SENSOR_LANDSCAPE,
        orientationPreferenceFor(screen),
      )
      assertEquals(
        GameChromePreference.FULLSCREEN,
        chromePreferenceFor(screen),
      )
    }
  }

  @Test
  fun `その他のゲーム画面は縦向きかつ標準表示を要求する`() {
    GameScreen.entries
      .filterNot { it == GameScreen.KLONDIKE || it == GameScreen.SPIDER }
      .forEach { screen ->
        assertEquals(
          "$screen should stay portrait",
          GameOrientationPreference.PORTRAIT,
          orientationPreferenceFor(screen),
        )
        assertEquals(
          "$screen should keep app chrome",
          GameChromePreference.STANDARD,
          chromePreferenceFor(screen),
        )
      }
  }
}
