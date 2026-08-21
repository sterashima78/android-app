package dev.terashima.yomitorirss.feature.library.data

import android.net.NetworkCapabilities
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.WorkInfo
import dev.terashima.yomitorirss.feature.library.SmbCoverPrefetchWorkerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SmbCoverPrefetchSchedulingTest {
  @Test
  fun `表紙先読みは従量制判定ではなくWi-Fi transportを要求する`() {
    val constraints = smbCoverPrefetchConstraints()
    val networkRequest = requireNotNull(constraints.requiredNetworkRequest)

    // API 28+ではNetworkRequestが実際の制約になり、NetworkTypeはNOT_REQUIREDになる。
    assertEquals(NetworkType.NOT_REQUIRED, constraints.requiredNetworkType)
    assertTrue(networkRequest.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
    assertFalse(networkRequest.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
    assertTrue(constraints.requiresBatteryNotLow())
  }

  @Test
  fun `Wi-Fi制約移行後の通常要求は既存チェーンへ追加する`() {
    assertEquals(
      ExistingWorkPolicy.APPEND_OR_REPLACE,
      smbCoverPrefetchExistingWorkPolicy(migrated = true, forceReschedule = false),
    )
  }

  @Test
  fun `Wi-Fi制約への初回移行は既存workを置き換える`() {
    assertEquals(
      ExistingWorkPolicy.REPLACE,
      smbCoverPrefetchExistingWorkPolicy(migrated = false, forceReschedule = false),
    )
  }

  @Test
  fun `明示的な再要求は移行済みでも既存workを置き換える`() {
    assertEquals(
      ExistingWorkPolicy.REPLACE,
      smbCoverPrefetchExistingWorkPolicy(migrated = true, forceReschedule = true),
    )
  }

  @Test
  fun `WorkManager状態は実行中を最優先で投影する`() {
    assertEquals(
      SmbCoverPrefetchWorkerState.RUNNING,
      smbCoverPrefetchWorkerState(
        listOf(
          WorkInfo.State.SUCCEEDED,
          WorkInfo.State.ENQUEUED,
          WorkInfo.State.RUNNING,
        ),
      ),
    )
  }

  @Test
  fun `active workがなければidleを投影する`() {
    assertEquals(
      SmbCoverPrefetchWorkerState.IDLE,
      smbCoverPrefetchWorkerState(listOf(WorkInfo.State.SUCCEEDED)),
    )
  }
}
