package dev.terashima.yomitorirss.feature.settings

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Fixed calendar boundary for on-device traffic diagnostics, independent of the device time zone. */
object TrafficUsageTime {
  val zone: ZoneId = ZoneId.of("Asia/Tokyo")

  fun dateAt(epochMillis: Long): LocalDate =
    Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

  fun startOfDayMillis(date: LocalDate): Long =
    date.atStartOfDay(zone).toInstant().toEpochMilli()
}
