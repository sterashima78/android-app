package dev.terashima.yomitorirss.feature.backup.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupFilesTest {
  @Test
  fun `日時を含むMosaicのZIPファイル名を生成する`() {
    val now = OffsetDateTime.parse("2026-08-06T20:52:00+09:00")

    assertEquals(
      "mosaic-auto-20260806T205200000+0900.zip",
      autoBackupFileName(now),
    )
  }

  @Test
  fun `Mosaic形式の新しい10世代だけを管理する`() {
    val backups = (1..12).map { index ->
      "mosaic-auto-202608${index.toString().padStart(2, '0')}T010000000+0900.zip"
    }
    val names = backups + listOf(
      "manual.zip",
      "yomitori-auto-20260701T010000000+0900-v2.zip",
      "mosaic-auto-invalid.txt",
    )

    assertEquals(
      backups.sortedDescending().drop(10).toSet(),
      obsoleteAutoBackupNames(names),
    )
  }
}
