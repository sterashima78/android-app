package dev.terashima.yomitorirss.feature.game

import org.junit.Assert.assertEquals
import org.junit.Test

class GameOrientationTest {
  @Test
  fun `スパイダーはCompose内で横向きかつ全画面を要求する`() {
    assertEquals(
      GameOrientationPreference.SENSOR_LANDSCAPE,
      orientationPreferenceFor(GameScreen.SPIDER),
    )
    assertEquals(
      GameChromePreference.FULLSCREEN,
      chromePreferenceFor(GameScreen.SPIDER),
    )
  }

  @Test
  fun `その他のComposeゲーム画面は縦向きかつ標準表示を要求する`() {
    GameScreen.entries
      .filterNot { it == GameScreen.SPIDER }
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

  @Test
  fun `クロンダイクは専用Godotシーンを起動する`() {
    assertEquals(
      listOf("--verbose", "--scene", "res://klondike.tscn"),
      klondikeGodotCommandLine(listOf("--verbose")),
    )
  }
}
