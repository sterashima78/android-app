package dev.terashima.yomitorirss.feature.summary.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SummaryQueueExecutionPreferencesTest {
  private val context: Context = ApplicationProvider.getApplicationContext()

  @Before
  fun clearPreferences() {
    context.getSharedPreferences(
      SummaryQueueExecutionPreferences.PREFERENCES_NAME,
      Context.MODE_PRIVATE,
    )
      .edit()
      .clear()
      .commit()
  }

  @Test
  fun `初期状態では自動実行中で充電時の自動再開が有効`() {
    val preferences = SummaryQueueExecutionPreferences(context)

    assertFalse(preferences.paused)
    assertTrue(preferences.resumeWhenCharging)
  }

  @Test
  fun `一時停止と充電時自動再開の設定を保存できる`() {
    SummaryQueueExecutionPreferences(context).apply {
      paused = true
      resumeWhenCharging = false
    }

    val reloaded = SummaryQueueExecutionPreferences(context)
    assertTrue(reloaded.paused)
    assertFalse(reloaded.resumeWhenCharging)
  }
}
