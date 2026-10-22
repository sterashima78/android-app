package dev.terashima.yomitorirss.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RssFeedTrafficSummaryTest {
  @Test
  fun `上位10件を受信量で並べ回線と取得契機と304比率を集計する`() {
    val records = (1..12).map { index ->
      RssFeedTrafficRecord(
        date = "2026-10-22",
        feedId = "feed-$index",
        feedTitle = "テストフィード$index",
        transport = "mobile",
        origin = "background",
        receivedBytes = index * 1_000L,
        requests = 2,
        notModified = 1,
      )
    } + listOf(
      RssFeedTrafficRecord("2026-10-22", "feed-12", "テストフィード12", "mobile", "screen_open", 500, 1, 1),
      RssFeedTrafficRecord("2026-10-22", "feed-12", "テストフィード12", "wifi", "manual", 999_999, 1, 0),
    )
    val overview = TrafficUsageOverview(emptyList(), null, null, records)

    val mobile = overview.topRssFeeds("mobile")
    assertEquals(10, mobile.size)
    assertEquals("feed-12", mobile.first().feedId)
    assertEquals(12_500L, mobile.first().receivedBytes)
    assertEquals(3L, mobile.first().requests)
    assertEquals(2L, mobile.first().notModified)
    assertEquals(66L, mobile.first().notModifiedPercent)
    assertEquals(mapOf("background" to 2L, "screen_open" to 1L), mobile.first().origins)
    assertEquals("feed-3", mobile.last().feedId)

    val all = overview.topRssFeeds("all")
    assertEquals(1_012_499L, all.first().receivedBytes)
    assertEquals(4L, all.first().requests)
  }

  @Test
  fun `削除済みフィードは表示名なしでも集計できる`() {
    val overview = TrafficUsageOverview(
      records = emptyList(),
      systemMobileBytes = null,
      systemWifiBytes = null,
      feedRecords = listOf(
        RssFeedTrafficRecord("2026-10-22", "removed-feed", null, "wifi", "background", 0, 2, 2),
      ),
    )

    val summary = overview.topRssFeeds("wifi").single()
    assertNull(summary.feedTitle)
    assertEquals(100L, summary.notModifiedPercent)
    assertEquals(0, overview.topRssFeeds("mobile").size)
  }
}
