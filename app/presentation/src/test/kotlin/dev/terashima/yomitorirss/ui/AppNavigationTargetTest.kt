package dev.terashima.yomitorirss.ui

import dev.terashima.yomitorirss.feature.bookmark.BOOKMARKS_ROUTE
import dev.terashima.yomitorirss.feature.library.LIBRARY_ROUTE
import dev.terashima.yomitorirss.feature.task.TASKS_ROUTE
import org.junit.Assert.assertEquals
import org.junit.Test

class AppNavigationTargetTest {
  @Test
  fun `外部起点の画面要求はpresentationでfeature routeへ解決する`() {
    assertEquals(BOOKMARKS_ROUTE, AppNavigationTarget.BOOKMARKS.appRoute())
    assertEquals(LIBRARY_ROUTE, AppNavigationTarget.LIBRARY.appRoute())
    assertEquals(TASKS_ROUTE, AppNavigationTarget.TASKS.appRoute())
  }
}
