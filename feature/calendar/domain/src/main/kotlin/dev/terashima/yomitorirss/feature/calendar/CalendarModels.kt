package dev.terashima.yomitorirss.feature.calendar

import java.time.Instant
import java.time.LocalDate

enum class CalendarEventSource {
  DEVICE_CALENDAR,
  TASK,
  WORKOUT,
}

enum class CalendarEventKind {
  SCHEDULE,
  DEADLINE,
  ACTIVITY,
}

sealed interface CalendarEventTime {
  data class Timed(
    val start: Instant,
    val endExclusive: Instant?,
  ) : CalendarEventTime

  data class AllDay(
    val startDate: LocalDate,
    val endDateExclusive: LocalDate,
  ) : CalendarEventTime
}

data class CalendarEventSourceMetadata(
  val sourceId: String? = null,
  val sourceName: String? = null,
  val sourceColorArgb: Int? = null,
)

data class CalendarEvent(
  val id: String,
  val source: CalendarEventSource,
  val kind: CalendarEventKind,
  val title: String,
  val description: String? = null,
  val location: String? = null,
  val time: CalendarEventTime,
  val sourceMetadata: CalendarEventSourceMetadata? = null,
)
