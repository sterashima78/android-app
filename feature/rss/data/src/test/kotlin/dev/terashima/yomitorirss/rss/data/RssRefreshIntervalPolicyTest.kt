package dev.terashima.yomitorirss.feature.rss.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RssRefreshIntervalPolicyTest {
  private val hourMillis = 60L * 60_000L

  @Test
  fun `individual feed interval takes priority over rule and scheduled baseline`() {
    assertEquals(3 * hourMillis, rssEffectiveMinimumAgeMillis(hourMillis, 180L, 360L))
    assertEquals(3 * hourMillis, rssEffectiveMinimumAgeMillis(15L * 60_000L, 180L, 360L))
  }

  @Test
  fun `matched rule interval takes priority when feed has no override`() {
    assertEquals(6 * hourMillis, rssEffectiveMinimumAgeMillis(hourMillis, null, 360L))
  }

  @Test
  fun `unconfigured sources inherit request interval`() {
    assertEquals(hourMillis, rssEffectiveMinimumAgeMillis(hourMillis, null, null))
  }

  @Test
  fun `force and import refresh ignore overrides`() {
    assertEquals(0L, rssEffectiveMinimumAgeMillis(0L, 180L, 360L))
  }

  @Test
  fun `three hour feed is not fetched on every hourly wakeup`() {
    val interval = rssEffectiveMinimumAgeMillis(hourMillis, 180L, null)
    val lastFetched = 1_000_000L
    assertFalse(rssIsRefreshDue(lastFetched + hourMillis, lastFetched, interval))
    assertFalse(rssIsRefreshDue(lastFetched + 2 * hourMillis, lastFetched, interval))
    assertTrue(rssIsRefreshDue(lastFetched + 3 * hourMillis, lastFetched, interval))
    assertTrue(rssIsRefreshDue(lastFetched + 4 * hourMillis, lastFetched, interval))
  }

  @Test
  fun `missing or future timestamps remain eligible`() {
    assertTrue(rssIsRefreshDue(100L, null, 3 * hourMillis))
    assertTrue(rssIsRefreshDue(100L, 200L, 3 * hourMillis))
  }
}
