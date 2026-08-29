package dev.terashima.yomitorirss.feature.backup.data

import android.net.NetworkCapabilities
import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GoogleDriveBackupSchedulerTest {
  @Test
  fun `Wi-Fi限定では検証済みWi-FiのNetworkRequestを要求する`() {
    val constraints = googleDriveBackupNetworkConstraints(wifiOnly = true)
    val request = requireNotNull(constraints.requiredNetworkRequest)

    assertTrue(request.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
    assertTrue(request.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
    assertTrue(request.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
  }

  @Test
  fun `Wi-Fi限定でなければ接続済みネットワークを要求する`() {
    val constraints = googleDriveBackupNetworkConstraints(wifiOnly = false)

    assertEquals(NetworkType.CONNECTED, constraints.requiredNetworkType)
    assertNull(constraints.requiredNetworkRequest)
  }
}
