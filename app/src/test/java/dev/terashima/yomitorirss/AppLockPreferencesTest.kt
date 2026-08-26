package dev.terashima.yomitorirss

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppLockPreferencesTest {
  private val context: Context = ApplicationProvider.getApplicationContext()

  @Before
  fun clearPreferences() {
    context.getSharedPreferences("app_lock", Context.MODE_PRIVATE).edit().clear().commit()
  }

  @Test
  fun `初期状態では生体認証ロックは無効`() {
    assertFalse(AppLockPreferences(context).enabled)
  }

  @Test
  fun `生体認証ロック設定を永続化する`() {
    AppLockPreferences(context).enabled = true

    assertTrue(AppLockPreferences(context).enabled)
  }
}
