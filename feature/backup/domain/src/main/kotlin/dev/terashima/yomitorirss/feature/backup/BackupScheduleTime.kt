package dev.terashima.yomitorirss.feature.backup

data class BackupScheduleTime(
  val hour: Int,
  val minute: Int,
) : Comparable<BackupScheduleTime> {
  init {
    require(hour in 0..23) { "hour must be between 0 and 23" }
    require(minute in 0..59) { "minute must be between 0 and 59" }
  }

  val encoded: String
    get() = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

  override fun compareTo(other: BackupScheduleTime): Int =
    compareValuesBy(this, other, BackupScheduleTime::hour, BackupScheduleTime::minute)

  companion object {
    fun parse(value: String): BackupScheduleTime? {
      val parts = value.split(':')
      if (parts.size != 2) return null
      val hour = parts[0].toIntOrNull() ?: return null
      val minute = parts[1].toIntOrNull() ?: return null
      return runCatching { BackupScheduleTime(hour, minute) }.getOrNull()
    }
  }
}
