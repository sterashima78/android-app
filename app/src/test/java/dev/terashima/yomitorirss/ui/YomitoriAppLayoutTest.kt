package dev.terashima.yomitorirss.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YomitoriAppLayoutTest {
  @Test
  fun `X ではグローバルヘッダーを表示しない`() {
    assertFalse(MainTab.X.usesGlobalTopBar())
  }

  @Test
  fun `X 以外ではグローバルヘッダーを表示する`() {
    MainTab.entries
      .filterNot { it == MainTab.X }
      .forEach { assertTrue(it.usesGlobalTopBar()) }
  }

  @Test
  fun `ゲームの全画面要求中だけapp chromeを隠す`() {
    assertTrue(shouldHideAppChrome(MainTab.GAME, gameFullscreen = true))
    assertFalse(shouldHideAppChrome(MainTab.GAME, gameFullscreen = false))
    assertFalse(shouldHideAppChrome(MainTab.HEALTH, gameFullscreen = true))
  }
}
