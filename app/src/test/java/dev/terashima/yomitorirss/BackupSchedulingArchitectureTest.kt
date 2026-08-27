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
      "feature/asset/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/asset/AssetManagementDialog.kt",
    )

    paths.forEach { path ->
      val source = repositoryFile(path).readText()
      assertFalse("$path must not depend on BackupChangeScheduler", source.contains("BackupChangeScheduler"))
      assertFalse("$path must not schedule backup explicitly", source.contains("scheduleBackupAfterChange"))
    }
  }

  @Test
  fun `app route wiringはfeature mutation後のbackupをorchestrateしない`() {
    val paths = listOf(
      "app/src/main/java/dev/terashima/yomitorirss/AppContentRouteDependencies.kt",
      "app/src/main/java/dev/terashima/yomitorirss/AppSupportingRouteDependencies.kt",
    )

    paths.forEach { path ->
      val source = repositoryFile(path).readText()
      assertFalse("$path must not wrap feature mutations for backup", source.contains("NotifyingWebLibraryMutator"))
      assertFalse("$path must not depend on app backup scheduler", source.contains("backupChangeScheduler"))
      assertFalse("$path must not schedule backup explicitly", source.contains("scheduleAfterChange"))
    }
  }

  @Test
  fun `主要なuser owned persistence pathはraw writable mutationを使わない`() {
    val paths = listOf(
      "feature/rss/data/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/data/FeedStore.kt",
      "feature/rss/data/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/data/RssWebScrapingRuleStore.kt",
      "feature/article/data/src/main/kotlin/dev/terashima/yomitorirss/feature/article/data/ArticleRepository.kt",
      "feature/article/data/src/main/kotlin/dev/terashima/yomitorirss/feature/article/data/BookmarkArticleGateway.kt",
      "feature/article/data/src/main/kotlin/dev/terashima/yomitorirss/feature/article/data/ContentSourceGateway.kt",
      "feature/bookmark/data/src/main/kotlin/dev/terashima/yomitorirss/feature/bookmark/data/BookmarkStateStore.kt",
      "feature/bookmark/data/src/main/kotlin/dev/terashima/yomitorirss/feature/bookmark/data/BookmarkTagStore.kt",
      "feature/bookmark/data/src/main/kotlin/dev/terashima/yomitorirss/feature/bookmark/data/BookmarkFolderStore.kt",
      "feature/bookmark/data/src/main/kotlin/dev/terashima/yomitorirss/feature/bookmark/data/BookmarkAssociationStore.kt",
      "feature/asset/data/src/main/kotlin/dev/terashima/yomitorirss/feature/asset/data/DefaultAssetRepository.kt",
      "feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/DefaultLibraryRepository.kt",
      "feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/DefaultWebLibraryMetadataExtractorRepository.kt",
      "feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/DefaultSmbLibraryRepository.kt",
      "feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/KindleStructuredSeriesMetadata.kt",
      "feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/AudibleStructuredSeriesMetadata.kt",
      "feature/mail/data/src/main/kotlin/dev/terashima/yomitorirss/feature/mail/data/DefaultMailRepository.kt",
      "feature/task/data/src/main/kotlin/dev/terashima/yomitorirss/feature/task/data/TaskStore.kt",
      "feature/youtube/data/src/main/kotlin/dev/terashima/yomitorirss/feature/youtube/data/YouTubeDatabase.kt",
    )
    val rawMutation = Regex(
      """database\.writable\s*\.\s*(?:insert\w*|update|delete|replace\w*|execSQL)\s*\(""",
    )

    paths.forEach { path ->
      val source = repositoryFile(path).readText()
      assertFalse("$path must use DatabaseConnection.write/transaction for user-owned mutations", rawMutation.containsMatchIn(source))
    }
  }

  @Test
  fun `applicationは永続化変更をbackup schedulerへbridgeする`() {
    val source = repositoryFile(
      "app/src/main/java/dev/terashima/yomitorirss/YomitoriApplication.kt",
    ).readText()

    assertTrue(source.contains("PersistenceChangeNotifier.shared.version.filter { it > 0L }"))
    assertFalse(source.contains("PersistenceChangeNotifier.shared.version.drop(1)"))
    assertTrue(source.contains("DatabaseBackupChangeObserver"))
    assertTrue(source.contains("AndroidBackupChangeScheduler"))
  }

  @Test
  fun `database snapshot restoreも永続化変更として通知する`() {
    val source = repositoryFile(
      "feature/backup/data/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/data/BackupRepository.kt",
    ).readText()

    assertTrue(source.contains("private val persistenceChanges: PersistenceChangeNotifier"))
    assertTrue(source.contains("persistenceChanges.notifyChanged()"))
    assertFalse(source.contains("scheduleAfterChange()"))
  }

  private fun repositoryFile(path: String): File {
    val start = File(System.getProperty("user.dir")).absoluteFile
    return generateSequence(start) { current -> current.parentFile }
      .map { root -> File(root, path) }
      .firstOrNull(File::isFile)
      ?: error("Repository source file not found: $path (start=$start)")
  }
}
