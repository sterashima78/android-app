package dev.terashima.yomitorirss.ui

import dev.terashima.yomitorirss.feature.bookmark.BOOKMARKS_ROUTE
import dev.terashima.yomitorirss.feature.integrated.ui.INTEGRATED_ROUTE
import dev.terashima.yomitorirss.feature.library.LIBRARY_ROUTE
import dev.terashima.yomitorirss.feature.settings.SETTINGS_ROUTE
import dev.terashima.yomitorirss.feature.task.TASKS_ROUTE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationTargetTest {
  @Test
  fun `外部起点の画面要求はpresentationでfeature routeへ解決する`() {
    assertEquals(INTEGRATED_ROUTE, AppNavigationTarget.INTEGRATED.appRoute())
    assertEquals(BOOKMARKS_ROUTE, AppNavigationTarget.BOOKMARKS.appRoute())
    assertEquals(LIBRARY_ROUTE, AppNavigationTarget.LIBRARY.appRoute())
    assertEquals(TASKS_ROUTE, AppNavigationTarget.TASKS.appRoute())
    assertEquals(SETTINGS_ROUTE, AppNavigationTarget.WEB_SERVER.appRoute())
  }

  @Test
  fun `Webサーバ要求だけが管理ダイアログを開く`() {
    assertTrue(AppNavigationTarget.WEB_SERVER.opensWebServerDialog())
    assertFalse(AppNavigationTarget.INTEGRATED.opensWebServerDialog())
  }
}
