package dev.terashima.yomitorirss.ui

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

}
