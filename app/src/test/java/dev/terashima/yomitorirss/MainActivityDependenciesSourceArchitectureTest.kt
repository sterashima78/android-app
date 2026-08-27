package dev.terashima.yomitorirss

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityDependenciesSourceArchitectureTest {
  private val repositoryRoot: File by lazy {
    generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
      .firstOrNull { File(it, "settings.gradle.kts").isFile && File(it, "app").isDirectory }
      ?: error("repository root not found")
  }

  @Test
  fun `共有Library追加はroute graphではなく専用capabilityから取得する`() {
    val dependencies = File(
      repositoryRoot,
      "app/src/main/java/dev/terashima/yomitorirss/MainActivityDependencies.kt",
    ).readText()
    val application = File(
      repositoryRoot,
      "app/src/main/java/dev/terashima/yomitorirss/YomitoriApplication.kt",
    ).readText()

    assertFalse(
      "external intent handling must not reach into Library route dependencies",
      "routeDependencies.library.addWebBook" in dependencies,
    )
    assertTrue(
      "MainActivityDependencies must receive a narrow shared-library capability",
      "private val addSharedWebBookCapability: suspend" in dependencies,
    )
    assertTrue(
      "Application composition must wire the shared-library capability directly",
      "addSharedWebBookCapability = container::addSharedWebBook" in application,
    )
    assertFalse(
      "shared Library mutation must not reintroduce caller-driven backup scheduling",
      "onChanged = container.backupChangeScheduler::scheduleAfterChange" in application,
    )
  }
}
