package dev.terashima.yomitorirss.entry

import dev.terashima.yomitorirss.feature.widget.WidgetLaunchContract
import dev.terashima.yomitorirss.ui.AppNavigationTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskWidgetLaunchRoutingTest {
  @Test
  fun `タスクウィジェット起動アクションはタスク画面要求へ解決する`() {
    assertEquals(
      AppNavigationTarget.TASKS,
      widgetLaunchTarget(WidgetLaunchContract.ACTION_OPEN_TASKS),
    )
  }

  @Test
  fun `無関係な起動アクションは画面要求へ解決しない`() {
    assertNull(widgetLaunchTarget("dev.terashima.yomitorirss.action.OTHER"))
    assertNull(widgetLaunchTarget(null))
  }
}
