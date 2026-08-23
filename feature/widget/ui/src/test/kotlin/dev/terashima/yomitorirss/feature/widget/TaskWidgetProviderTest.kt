package dev.terashima.yomitorirss.feature.widget

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class TaskWidgetProviderTest {
  @Test
  fun `タスク起動Intentにはタスク画面アクションと再利用用flagsを設定する`() {
    val intent = Intent().configureTaskWidgetLaunch()

    assertEquals(TaskWidgetProvider.ACTION_OPEN_TASKS, intent.action)
    assertTrue(intent.hasFlag(Intent.FLAG_ACTIVITY_NEW_TASK))
    assertTrue(intent.hasFlag(Intent.FLAG_ACTIVITY_CLEAR_TOP))
    assertTrue(intent.hasFlag(Intent.FLAG_ACTIVITY_SINGLE_TOP))
  }

  private fun Intent.hasFlag(flag: Int): Boolean = flags and flag == flag
}
