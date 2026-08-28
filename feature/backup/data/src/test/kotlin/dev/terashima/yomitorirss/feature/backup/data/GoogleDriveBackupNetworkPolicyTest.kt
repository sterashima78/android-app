package dev.terashima.yomitorirss.feature.backup.data

import android.net.NetworkCapabilities
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GoogleDriveBackupNetworkPolicyTest {
  @Test
  fun `検証済みWi-Fiをバックアップ可能と判定する`() {
    val capabilities = capabilities(
      transport = NetworkCapabilities.TRANSPORT_WIFI,
      validated = true,
    )

    assertTrue(capabilities.isValidatedWifiForGoogleDriveBackup())
  }

  @Test
  fun `モバイル回線はバックアップ可能なWi-Fiと判定しない`() {
    val capabilities = capabilities(
      transport = NetworkCapabilities.TRANSPORT_CELLULAR,
      validated = true,
    )

    assertFalse(capabilities.isValidatedWifiForGoogleDriveBackup())
  }

  @Test
  fun `未検証Wi-Fiはバックアップ可能と判定しない`() {
    val capabilities = capabilities(
      transport = NetworkCapabilities.TRANSPORT_WIFI,
      validated = false,
    )

    assertFalse(capabilities.isValidatedWifiForGoogleDriveBackup())
  }

  private fun capabilities(transport: Int, validated: Boolean): NetworkCapabilities {
    val capabilities = ShadowNetworkCapabilities.newInstance()
    shadowOf(capabilities)
      .addTransportType(transport)
      .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    if (validated) {
      shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    return capabilities
  }
}
