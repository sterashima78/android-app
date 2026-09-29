package dev.terashima.yomitorirss.feature.backup.data

import android.net.NetworkCapabilities
import androidx.work.NetworkType
import dev.terashima.yomitorirss.feature.backup.BackupScheduleTime
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
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

  @Test
  fun `当日の未来時刻までのdelayを計算する`() {
    val now = ZonedDateTime.of(2026, 9, 29, 10, 15, 0, 0, ZoneId.of("Asia/Tokyo"))

    val delay = GoogleDriveBackupScheduler.nextBackupDelayMillis(
      now = now,
      time = BackupScheduleTime(18, 30),
    )

    assertEquals(Duration.ofHours(8).plusMinutes(15).toMillis(), delay)
  }

  @Test
  fun `経過済み時刻は翌日に予約する`() {
    val now = ZonedDateTime.of(2026, 9, 29, 21, 0, 0, 0, ZoneId.of("Asia/Tokyo"))

    val delay = GoogleDriveBackupScheduler.nextBackupDelayMillis(
      now = now,
      time = BackupScheduleTime(8, 0),
    )

    assertEquals(Duration.ofHours(11).toMillis(), delay)
  }
}
