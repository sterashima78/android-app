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
    listOf(
      SummaryQueueExecutionPreferences.LOCAL_PREFERENCES_NAME,
      SummaryQueueExecutionPreferences.CLOUD_PREFERENCES_NAME,
    ).forEach { name ->
      context.getSharedPreferences(name, Context.MODE_PRIVATE)
        .edit()
        .clear()
        .commit()
    }
  }

  @Test
  fun `初期状態ではローカルとクラウドが実行可能でローカル充電再開が有効`() {
    val preferences = SummaryQueueExecutionPreferences(context)

    assertFalse(preferences.localPaused)
    assertFalse(preferences.cloudPaused)
    assertTrue(preferences.resumeLocalWhenCharging)
  }

  @Test
  fun `ローカルとクラウドの一時停止を独立して保存できる`() {
    SummaryQueueExecutionPreferences(context).apply {
      localPaused = true
      cloudPaused = false
      resumeLocalWhenCharging = false
    }

    val reloaded = SummaryQueueExecutionPreferences(context)
    assertTrue(reloaded.localPaused)
    assertFalse(reloaded.cloudPaused)
    assertFalse(reloaded.resumeLocalWhenCharging)

    reloaded.cloudPaused = true
    val again = SummaryQueueExecutionPreferences(context)
    assertTrue(again.localPaused)
    assertTrue(again.cloudPaused)
  }
}
