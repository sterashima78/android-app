package dev.terashima.yomitorirss

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupSchedulingArchitectureTest {
  @Test
  fun `featureのpresentation層はbackup schedulingを所有しない`() {
    val paths = listOf(
      "feature/rss/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/RssViewModel.kt",
      "feature/rss/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/FeedViewModel.kt",
      "feature/reddit/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/reddit/RedditViewModel.kt",
      "feature/bookmark/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/bookmark/BookmarkViewModel.kt",
      "feature/youtube/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/youtube/YouTubeViewModel.kt",
      "feature/knowledge/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/knowledge/KnowledgeViewModel.kt",
    )

    paths.forEach { path ->
      val source = repositoryFile(path).readText()
      assertFalse("$path must not depend on BackupChangeScheduler", source.contains("BackupChangeScheduler"))
      assertFalse("$path must not schedule backup explicitly", source.contains("scheduleBackupAfterChange"))
    }
  }

  @Test
  fun `app route wiringはfeature mutation後のbackupをorchestrateしない`() {
    val source = repositoryFile(
      "app/src/main/java/dev/terashima/yomitorirss/AppContentRouteDependencies.kt",
    ).readText()

    assertFalse(source.contains("NotifyingWebLibraryMutator"))
    assertFalse(source.contains("backupChangeScheduler"))
    assertFalse(source.contains("scheduleAfterChange"))
  }

  @Test
  fun `applicationは永続化変更をbackup schedulerへbridgeする`() {
    val source = repositoryFile(
      "app/src/main/java/dev/terashima/yomitorirss/YomitoriApplication.kt",
    ).readText()

    assertTrue(source.contains("PersistenceChangeNotifier.shared.version.drop(1)"))
    assertTrue(source.contains("DatabaseBackupChangeObserver"))
    assertTrue(source.contains("AndroidBackupChangeScheduler"))
  }

  private fun repositoryFile(path: String): File {
    val start = File(System.getProperty("user.dir")).absoluteFile
    return generateSequence(start) { current -> current.parentFile }
      .map { root -> File(root, path) }
      .firstOrNull(File::isFile)
      ?: error("Repository source file not found: $path (start=$start)")
  }
}
