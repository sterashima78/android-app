package dev.terashima.yomitorirss.feature.settings

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class TrafficUsageTimeTest {
  @Test
  fun `JSTの0時を境に記録日が切り替わる`() {
    val beforeMidnight = Instant.parse("2026-11-02T14:59:59Z").toEpochMilli()
    val atMidnight = Instant.parse("2026-11-02T15:00:00Z").toEpochMilli()

    assertEquals(LocalDate.parse("2026-11-02"), TrafficUsageTime.dateAt(beforeMidnight))
    assertEquals(LocalDate.parse("2026-11-03"), TrafficUsageTime.dateAt(atMidnight))
  }

  @Test
  fun `集計開始時刻はJSTの0時をUTCに変換する`() {
    assertEquals(
      Instant.parse("2026-11-02T15:00:00Z").toEpochMilli(),
      TrafficUsageTime.startOfDayMillis(LocalDate.parse("2026-11-03")),
    )
  }
}
