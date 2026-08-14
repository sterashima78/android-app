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
}
