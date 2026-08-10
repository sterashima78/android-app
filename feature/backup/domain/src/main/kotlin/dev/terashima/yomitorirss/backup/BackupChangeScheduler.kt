package dev.terashima.yomitorirss.feature.backup

fun interface BackupChangeScheduler {
  fun scheduleAfterChange()
}
