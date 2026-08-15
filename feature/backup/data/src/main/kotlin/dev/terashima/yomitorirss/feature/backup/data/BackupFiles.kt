package dev.terashima.yomitorirss.feature.backup.data

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

internal const val AUTO_BACKUP_PREFIX = "yomitori-auto-"
internal const val AUTO_BACKUP_SUFFIX = "-v1.json"
internal const val AUTO_BACKUP_RETENTION = 10

private val backupFileTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssSSSXX")

internal fun autoBackupFileName(now: OffsetDateTime = OffsetDateTime.now()): String =
  "$AUTO_BACKUP_PREFIX${backupFileTimeFormatter.format(now)}$AUTO_BACKUP_SUFFIX"

internal fun obsoleteAutoBackupNames(
  names: List<String>,
  keep: Int = AUTO_BACKUP_RETENTION,
): Set<String> = names
  .filter { it.startsWith(AUTO_BACKUP_PREFIX) && it.endsWith(AUTO_BACKUP_SUFFIX) }
  .sortedDescending()
  .drop(keep.coerceAtLeast(0))
  .toSet()
