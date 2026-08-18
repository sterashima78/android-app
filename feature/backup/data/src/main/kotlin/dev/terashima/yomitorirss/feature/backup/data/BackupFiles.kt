package dev.terashima.yomitorirss.feature.backup.data

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

internal const val AUTO_BACKUP_PREFIX = "mosaic-auto-"
internal const val AUTO_BACKUP_SUFFIX = ".zip"
internal const val AUTO_BACKUP_RETENTION = 10

private val backupFileTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssSSSXX")

internal fun autoBackupFileName(now: OffsetDateTime = OffsetDateTime.now()): String =
  "$AUTO_BACKUP_PREFIX${backupFileTimeFormatter.format(now)}$AUTO_BACKUP_SUFFIX"

internal fun obsoleteAutoBackupNames(
  names: List<String>,
  keep: Int = AUTO_BACKUP_RETENTION,
): Set<String> = names
  .filter(::isManagedAutoBackup)
  .sortedDescending()
  .drop(keep.coerceAtLeast(0))
  .toSet()

private fun isManagedAutoBackup(name: String): Boolean =
  name.startsWith(AUTO_BACKUP_PREFIX) && name.endsWith(AUTO_BACKUP_SUFFIX)
