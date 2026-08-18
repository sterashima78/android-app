package dev.terashima.yomitorirss.feature.backup.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupFilesTest {
  @Test
  fun `日時と形式バージョンを含むZIPファイル名を生成する`() {
    val now = OffsetDateTime.parse("2026-08-06T20:52:00+09:00")

    assertEquals(
      "yomitori-auto-20260806T205200000+0900-v2.zip",
      autoBackupFileName(now),
    )
  }

  @Test
  fun `新旧形式を合わせて新しい10世代を残す`() {
    val currentBackups = (1..8).map { index ->
      "yomitori-auto-202608${index.toString().padStart(2, '0')}T010000000+0900-v2.zip"
    }
    val legacyBackups = (9..12).map { index ->
      "yomitori-auto-202607${index.toString().padStart(2, '0')}T010000000+0900-v1.json"
    }
    val backups = currentBackups + legacyBackups
    val names = backups + listOf("manual.json", "yomitori-auto-invalid.txt")

    assertEquals(
      backups.sortedDescending().drop(10).toSet(),
      obsoleteAutoBackupNames(names),
    )
  }
}
