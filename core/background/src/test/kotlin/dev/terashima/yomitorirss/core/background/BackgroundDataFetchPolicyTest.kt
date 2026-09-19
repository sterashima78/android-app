package dev.terashima.yomitorirss.core.background

import androidx.test.core.app.ApplicationProvider
import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackgroundDataFetchPolicyTest {
  @Test
  fun `wifiOnlyが無効なら接続済みnetworkを要求し現在networkに依存せず許可する`() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    BackgroundDataFetchPreferences(context).wifiOnly = false

    val constraints = backgroundDataFetchConstraints(context)

    assertEquals(NetworkType.CONNECTED, constraints.requiredNetworkType)
    assertTrue(isBackgroundDataFetchAllowed(context))
  }

  @Test
  fun `wifiOnlyが有効でactive networkがなければfetchを許可しない`() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    BackgroundDataFetchPreferences(context).wifiOnly = true

    assertFalse(isBackgroundDataFetchAllowed(context))
  }

  @Test
  fun `refresh intervalは許可値だけを保存する`() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val preferences = BackgroundDataFetchPreferences(context)

    preferences.integratedRefreshIntervalMinutes = 180L

    assertEquals(180L, preferences.integratedRefreshIntervalMinutes)
  }

  @Test(expected = IllegalArgumentException::class)
  fun `未対応refresh intervalは拒否する`() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    BackgroundDataFetchPreferences(context).integratedRefreshIntervalMinutes = 1L
  }
}
