package dev.terashima.yomitorirss.feature.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarEventOccurrenceTest {
  private val tokyo = ZoneId.of("Asia/Tokyo")

  @Test
  fun `複数日の終日イベントは終了日を含まない`() {
    val event = CalendarEvent(
      id = "all-day",
      source = CalendarEventSource.DEVICE_CALENDAR,
      kind = CalendarEventKind.SCHEDULE,
      title = "休暇",
      time = CalendarEventTime.AllDay(
        startDate = LocalDate.of(2026, 8, 19),
        endDateExclusive = LocalDate.of(2026, 8, 21),
      ),
    )

    assertTrue(event.occursOn(LocalDate.of(2026, 8, 19), tokyo))
    assertTrue(event.occursOn(LocalDate.of(2026, 8, 20), tokyo))
    assertFalse(event.occursOn(LocalDate.of(2026, 8, 21), tokyo))
  }

  @Test
  fun `日付をまたぐ時刻イベントは両日に表示する`() {
    val event = CalendarEvent(
      id = "timed",
      source = CalendarEventSource.DEVICE_CALENDAR,
      kind = CalendarEventKind.SCHEDULE,
      title = "深夜作業",
      time = CalendarEventTime.Timed(
        start = Instant.parse("2026-08-19T14:30:00Z"),
        endExclusive = Instant.parse("2026-08-19T16:30:00Z"),
      ),
    )

    assertTrue(event.occursOn(LocalDate.of(2026, 8, 19), tokyo))
    assertTrue(event.occursOn(LocalDate.of(2026, 8, 20), tokyo))
  }
}
