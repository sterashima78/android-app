package dev.terashima.yomitorirss.core.background

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackgroundDataFetchPreferencesTest {
  private val context: Context = ApplicationProvider.getApplicationContext()

  @Before
  fun clearPreferences() {
    context.getSharedPreferences("background_data_fetch", Context.MODE_PRIVATE)
      .edit()
      .clear()
      .commit()
  }

  @Test
  fun `Wi-Fi限定は初期状態で無効`() {
    assertFalse(BackgroundDataFetchPreferences(context).wifiOnly)
  }

  @Test
  fun `Wi-Fi限定を保存して再読込できる`() {
    BackgroundDataFetchPreferences(context).wifiOnly = true

    assertTrue(BackgroundDataFetchPreferences(context).wifiOnly)
  }

  @Test
  fun `統合ビュー更新間隔は初期状態で1時間`() {
    assertEquals(60L, BackgroundDataFetchPreferences(context).integratedRefreshIntervalMinutes)
  }

  @Test
  fun `統合ビュー更新間隔を保存して再読込できる`() {
    BackgroundDataFetchPreferences(context).integratedRefreshIntervalMinutes = 180L

    assertEquals(180L, BackgroundDataFetchPreferences(context).integratedRefreshIntervalMinutes)
  }
}
