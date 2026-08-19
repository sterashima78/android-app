package dev.terashima.yomitorirss.feature.calendar

import java.time.LocalDate

interface CalendarRepository {
  suspend fun events(
    fromInclusive: LocalDate,
    untilExclusive: LocalDate,
  ): List<CalendarEvent>
}
