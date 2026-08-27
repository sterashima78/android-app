package dev.terashima.yomitorirss.entry

import dev.terashima.yomitorirss.feature.task.TASKS_ROUTE
import dev.terashima.yomitorirss.feature.widget.TaskWidgetProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskWidgetLaunchRoutingTest {
  @Test
  fun `タスクウィジェット起動アクションはタスクrouteへ解決する`() {
    assertEquals(
      TASKS_ROUTE,
      widgetLaunchRoute(TaskWidgetProvider.ACTION_OPEN_TASKS),
    )
  }

  @Test
  fun `無関係な起動アクションはroute指定へ解決しない`() {
    assertNull(widgetLaunchRoute("dev.terashima.yomitorirss.action.OTHER"))
    assertNull(widgetLaunchRoute(null))
  }
}
