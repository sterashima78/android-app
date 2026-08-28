package dev.terashima.yomitorirss.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RootBackActionTest {
  @Test
  fun `戻れる画面がある場合はnavigationを戻す`() {
    assertEquals(
      RootBackAction.POP_NAVIGATION,
      rootBackAction(isDrawerOpen = false, canNavigateBack = true),
    )
  }

  @Test
  fun `rootでドロワーが閉じている場合はドロワーを開く`() {
    assertEquals(
      RootBackAction.OPEN_DRAWER,
      rootBackAction(isDrawerOpen = false, canNavigateBack = false),
    )
  }

  @Test
  fun `ドロワーが開いている場合はnavigation履歴より優先してアプリを終了する`() {
    assertEquals(
      RootBackAction.EXIT_APP,
      rootBackAction(isDrawerOpen = true, canNavigateBack = true),
    )
  }
}
