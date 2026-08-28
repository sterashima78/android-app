package dev.terashima.yomitorirss

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class MainActivityApplicationServiceArchitectureTest {
  @Test
  fun `MainActivityはBookmark保存後のbackupをorchestrateしない`() {
    val source = repositoryFile(
      "app/src/main/java/dev/terashima/yomitorirss/MainActivity.kt",
    ).readText()

    assertFalse(source.contains("scheduleBackupAfterBookmarkChange"))
    assertFalse(source.contains("BackupChangeScheduler"))
  }

  private fun repositoryFile(path: String): File {
    val start = File(System.getProperty("user.dir")).absoluteFile
    return generateSequence(start) { current -> current.parentFile }
      .map { root -> File(root, path) }
      .firstOrNull(File::isFile)
      ?: error("Repository source file not found: $path (start=$start)")
  }
}
