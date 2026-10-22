package dev.terashima.yomitorirss.rss.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.feature.rss.data.RssRefreshPreferences
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RssRefreshPreferencesTest {
  private val context: Context = ApplicationProvider.getApplicationContext()

  @Before
  fun reset() {
    context.getSharedPreferences("rss_refresh", Context.MODE_PRIVATE).edit().clear().commit()
  }

  @Test
  fun `デフォルトは画面再取得30分と失敗再試行5分`() {
    val settings = RssRefreshPreferences(context)
    assertEquals(30L, settings.screenRefreshIntervalMinutes)
    assertEquals(5L, settings.failureRetryIntervalMinutes)
  }

  @Test
  fun `変更した取得間隔は永続化される`() {
    val settings = RssRefreshPreferences(context)
    settings.screenRefreshIntervalMinutes = 180L
    settings.failureRetryIntervalMinutes = 15L

    val restored = RssRefreshPreferences(context)
    assertEquals(180L, restored.screenRefreshIntervalMinutes)
    assertEquals(15L, restored.failureRetryIntervalMinutes)
    assertEquals(180L, restored.intervals().automaticMinutes)
    assertEquals(15L, restored.intervals().failureRetryMinutes)
  }

  @Test
  fun `不正な保存値は安全な既定値に戻す`() {
    context.getSharedPreferences("rss_refresh", Context.MODE_PRIVATE).edit()
      .putLong("screen_refresh_interval_minutes", -5L)
      .putLong("failure_retry_interval_minutes", 999L)
      .commit()
    val restored = RssRefreshPreferences(context)
    assertEquals(30L, restored.screenRefreshIntervalMinutes)
    assertEquals(5L, restored.failureRetryIntervalMinutes)
  }
}
