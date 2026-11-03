package dev.terashima.yomitorirss.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficDailySummaryTest {
  private val overview = TrafficUsageOverview(
    records = listOf(
      TrafficUsageRecord("2026-11-03", "rss", "mobile", 40, 5),
      TrafficUsageRecord("2026-11-03", "video", "mobile", 300, 0),
      TrafficUsageRecord("2026-11-03", "rss", "wifi", 100, 10),
      TrafficUsageRecord("2026-11-02", "wiki", "mobile", 20, 2),
      TrafficUsageRecord("2026-11-02", "wiki", "unknown", 8, 0),
    ),
    systemMobileBytes = null,
    systemWifiBytes = null,
  )

  @Test
  fun `日別ログは日付降順で回線ごとの送受信量と機能内訳を返す`() {
    val mobile = overview.dailySummaries("mobile")

    assertEquals(listOf("2026-11-03", "2026-11-02"), mobile.map { it.date })
    assertEquals(340L, mobile.first().receivedBytes)
    assertEquals(5L, mobile.first().sentBytes)
    assertEquals(345L, mobile.first().totalBytes)
    assertEquals(listOf("video", "rss"), mobile.first().features.map { it.feature })
    assertEquals(300L, mobile.first().features.first().totalBytes)
    assertEquals(22L, mobile.last().totalBytes)
  }

  @Test
  fun `合計回線は各機能を日付内で合算し回線不明も含む`() {
    val all = overview.dailySummaries("all")

    assertEquals(455L, all.first().totalBytes)
    assertEquals(155L, all.first().features.single { it.feature == "rss" }.totalBytes)
    assertEquals(30L, all.last().totalBytes)
    assertEquals(110L, overview.dailySummaries("wifi").single().totalBytes)
    assertTrue(overview.dailySummaries("no-such-transport").isEmpty())
  }
}
