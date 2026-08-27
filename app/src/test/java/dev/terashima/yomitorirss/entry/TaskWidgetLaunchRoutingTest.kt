package dev.terashima.yomitorirss.entry

import dev.terashima.yomitorirss.feature.widget.TaskWidgetProvider
import dev.terashima.yomitorirss.ui.MainTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskWidgetLaunchRoutingTest {
  @Test
  fun `タスクウィジェット起動アクションはタスクタブへ解決する`() {
    assertEquals(
      MainTab.TASKS,
      widgetLaunchTab(TaskWidgetProvider.ACTION_OPEN_TASKS),
    )
  }

  @Test
  fun `無関係な起動アクションはタブ指定へ解決しない`() {
    assertNull(widgetLaunchTab("dev.terashima.yomitorirss.action.OTHER"))
    assertNull(widgetLaunchTab(null))
  }
}
