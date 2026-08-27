package dev.terashima.yomitorirss.ui

import dev.terashima.yomitorirss.feature.game.GAME_ROUTE
import dev.terashima.yomitorirss.feature.health.HEALTH_ROUTE
import dev.terashima.yomitorirss.feature.x.X_ROUTE
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YomitoriAppLayoutTest {
  @Test
  fun `Xではグローバルヘッダーを表示しない`() {
    assertFalse(X_ROUTE.usesGlobalTopBar())
  }

  @Test
  fun `X以外ではグローバルヘッダーを表示する`() {
    allAppRoutes
      .filterNot { it == X_ROUTE }
      .forEach { assertTrue(it.usesGlobalTopBar()) }
  }

  @Test
  fun `ゲームの全画面要求中だけapp chromeを隠す`() {
    assertTrue(shouldHideAppChrome(GAME_ROUTE, gameFullscreen = true))
    assertFalse(shouldHideAppChrome(GAME_ROUTE, gameFullscreen = false))
    assertFalse(shouldHideAppChrome(HEALTH_ROUTE, gameFullscreen = true))
  }
}
