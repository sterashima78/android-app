package dev.terashima.yomitorirss.feature.backup
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleDriveBackupStatusTest {
  @Test
  fun `フォルダURIがある場合だけ設定済みになる`() {
    assertFalse(GoogleDriveBackupStatus().configured)
    assertTrue(GoogleDriveBackupStatus(folderUri = "content://drive/folder").configured)
  }
}
