package dev.terashima.yomitorirss.composition

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegratedBackgroundRefreshArchitectureTest {
  private val repositoryRoot: File by lazy {
    generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
      .firstOrNull { File(it, "settings.gradle.kts").isFile && File(it, "app").isDirectory }
      ?: error("repository root not found")
  }

  @Test
  fun `統合周期更新はapp compositionのunique WorkManager jobが所有する`() {
    val worker = source(
      "app/composition/src/main/java/dev/terashima/yomitorirss/composition/background/IntegratedRefreshWorker.kt",
    )
    val workerFactory = source(
      "app/composition/src/main/java/dev/terashima/yomitorirss/AppWorkerFactory.kt",
    )
    val startup = source(
      "app/composition/src/main/java/dev/terashima/yomitorirss/composition/background/AppBackgroundRuntime.kt",
    )

    assertTrue("integrated refresh must use unique periodic work", "enqueueUniquePeriodicWork" in worker)
    assertTrue("interval changes must update the same periodic work", "ExistingPeriodicWorkPolicy.UPDATE" in worker)
    assertTrue("worker must be constructor-injected from AppWorkerFactory", "IntegratedRefreshWorkerFactory(container)" in workerFactory)
    assertTrue("startup must schedule integrated refresh", "IntegratedRefreshScheduler.schedule(application)" in startup)
  }

  @Test
  fun `Gmailの旧周期jobは再作成せず初回同期workerだけを維持する`() {
    val mailWorker = source(
      "feature/mail/data/src/main/kotlin/dev/terashima/yomitorirss/feature/mail/data/MailSyncWorker.kt",
    )
    val startup = source(
      "app/composition/src/main/java/dev/terashima/yomitorirss/composition/background/AppBackgroundRuntime.kt",
    )

    assertFalse(
      "standalone mail periodic work must not be created after migration",
      "PeriodicWorkRequestBuilder<MailSyncWorker>" in mailWorker,
    )
    assertTrue("legacy mail periodic work must be cancelled at startup", "MailSyncScheduler(application).cancelPeriodic()" in startup)
    assertTrue("initial mail paging work must remain durable", "OneTimeWorkRequestBuilder<MailSyncWorker>()" in mailWorker)
  }

  @Test
  fun `通知permissionはsettings経由のユーザー操作で要求する`() {
    val settingsRoute = source(
      "app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/SettingsRoute.kt",
    )
    val settingsContent = source(
      "feature/settings/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/SettingsContent.kt",
    )

    assertTrue("app presentation must own RequestPermission", "ActivityResultContracts.RequestPermission()" in settingsRoute)
    assertTrue("settings must expose notification purpose before requesting", "新着通知を有効にする" in settingsContent)
  }

  private fun source(path: String): String = File(repositoryRoot, path).readText()
}
