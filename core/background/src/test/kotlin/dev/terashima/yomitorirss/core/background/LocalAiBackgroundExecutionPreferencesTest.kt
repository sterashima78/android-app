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
class LocalAiBackgroundExecutionPreferencesTest {
  private val context: Context = ApplicationProvider.getApplicationContext()

  @Before
  fun clearPreferences() {
    context.getSharedPreferences(
      LocalAiBackgroundExecutionPreferences.PREFERENCES_NAME,
      Context.MODE_PRIVATE,
    ).edit().clear().commit()
    context.getSharedPreferences(
      LocalAiBackgroundExecutionPreferences.LEGACY_SUMMARY_PREFERENCES_NAME,
      Context.MODE_PRIVATE,
    ).edit().clear().commit()
  }

  @Test
  fun `初期状態ではAIバックグラウンド実行中で充電時再開が有効`() {
    val preferences = LocalAiBackgroundExecutionPreferences(context)

    assertFalse(preferences.paused)
    assertTrue(preferences.resumeWhenCharging)
  }

  @Test
  fun `旧要約キューの実行設定を共通AI実行設定へ移行する`() {
    context.getSharedPreferences(
      LocalAiBackgroundExecutionPreferences.LEGACY_SUMMARY_PREFERENCES_NAME,
      Context.MODE_PRIVATE,
    ).edit()
      .putBoolean(LocalAiBackgroundExecutionPreferences.KEY_PAUSED, true)
      .putBoolean(LocalAiBackgroundExecutionPreferences.KEY_RESUME_WHEN_CHARGING, false)
      .commit()

    val preferences = LocalAiBackgroundExecutionPreferences(context)

    assertTrue(preferences.paused)
    assertFalse(preferences.resumeWhenCharging)
  }

  @Test
  fun `共通AI実行設定を永続化できる`() {
    LocalAiBackgroundExecutionPreferences(context).apply {
      paused = true
      resumeWhenCharging = false
    }

    val reloaded = LocalAiBackgroundExecutionPreferences(context)
    assertTrue(reloaded.paused)
    assertFalse(reloaded.resumeWhenCharging)
  }
}
