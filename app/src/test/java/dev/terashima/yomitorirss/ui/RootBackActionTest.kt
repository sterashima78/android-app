package dev.terashima.yomitorirss.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RootBackActionTest {
  @Test
  fun `ドロワーが閉じている場合はドロワーを開く`() {
    assertEquals(RootBackAction.OPEN_DRAWER, rootBackAction(isDrawerOpen = false))
  }

  @Test
  fun `ドロワーが開いている場合はアプリを終了する`() {
    assertEquals(RootBackAction.EXIT_APP, rootBackAction(isDrawerOpen = true))
  }
}
