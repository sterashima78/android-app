package dev.terashima.yomitorirss.feature.backup.data
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupFilesTest {
  @Test
  fun `日時と形式バージョンを含むファイル名を生成する`() {
    val now = OffsetDateTime.parse("2026-08-06T20:52:00+09:00")

    assertEquals(
      "yomitori-auto-20260806T205200000+0900-v1.json",
      autoBackupFileName(now),
    )
  }

  @Test
  fun `新しい10世代を残して古い自動バックアップだけを削除対象にする`() {
    val autoBackups = (1..12).map { index ->
      "yomitori-auto-202608${index.toString().padStart(2, '0')}T010000000+0900-v1.json"
    }
    val names = autoBackups + listOf("manual.json", "yomitori-auto-invalid.txt")

    assertEquals(
      autoBackups.sortedDescending().drop(10).toSet(),
      obsoleteAutoBackupNames(names),
    )
  }
}
