package dev.terashima.yomitorirss.core.background

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CloudAiBackgroundExecutionPreferencesTest {
  private val context: Context = ApplicationProvider.getApplicationContext()

  @Before
  fun clearPreferences() {
    context.getSharedPreferences(CloudAiBackgroundExecutionPreferences.PREFERENCES_NAME, Context.MODE_PRIVATE)
      .edit()
      .clear()
      .commit()
  }

  @Test
  fun `cloud AI is active by default and pause is persisted`() {
    val preferences = CloudAiBackgroundExecutionPreferences(context)
    assertFalse(preferences.paused)

    preferences.paused = true

    assertTrue(CloudAiBackgroundExecutionPreferences(context).paused)
  }
}
