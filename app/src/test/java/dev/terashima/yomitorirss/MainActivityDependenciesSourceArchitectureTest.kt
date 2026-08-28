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
  fun `MainActivity dependencyはpresentationとincoming intentのfacadeに分離する`() {
    val legacyDependencies = File(
      repositoryRoot,
      "app/src/main/java/dev/terashima/yomitorirss/MainActivityDependencies.kt",
    )
    val provider = File(
      repositoryRoot,
      "app/src/main/java/dev/terashima/yomitorirss/MainActivityDependenciesProvider.kt",
    ).readText()
    val presentationDependencies = File(
      repositoryRoot,
      "app/src/main/java/dev/terashima/yomitorirss/MainActivityPresentationDependencies.kt",
    ).readText()
    val incomingDependencies = File(
      repositoryRoot,
      "app/src/main/java/dev/terashima/yomitorirss/entry/IncomingIntentDependencies.kt",
    ).readText()
    val mainActivity = File(
      repositoryRoot,
      "app/src/main/java/dev/terashima/yomitorirss/MainActivity.kt",
    ).readText()

    assertFalse("combined MainActivity dependency facade must not return", legacyDependencies.exists())
    assertTrue(
      "framework provider must expose the presentation facade",
      "val mainActivityPresentationDependencies: MainActivityPresentationDependencies" in provider,
    )
    assertTrue(
      "framework provider must expose the incoming intent facade",
      "val incomingIntentDependencies: IncomingIntentDependencies" in provider,
    )
    assertTrue(
      "presentation facade must own route composition",
      "val routeDependencies: AppRouteDependencies" in presentationDependencies,
    )
    assertTrue(
      "presentation facade must own LAN Web presentation connection",
      "val lanWebServerController: LanWebServerController" in presentationDependencies,
    )
    assertFalse(
      "presentation facade must not own external Intent mutation capabilities",
      "SaveSharedBookmarkUseCase" in presentationDependencies || "LibraryBook" in presentationDependencies,
    )
    assertFalse(
      "incoming Intent facade must not depend on route or LAN Web presentation composition",
      "AppRouteDependencies" in incomingDependencies || "LanWebServerController" in incomingDependencies,
    )
    assertTrue(
      "MainActivity must pass only incoming Intent dependencies to the handler",
      "dependencies = incomingIntentDependencies" in mainActivity,
    )
    assertTrue(
      "MainActivity presentation must use only the presentation facade",
      "presentationDependencies.routeDependencies" in mainActivity &&
        "presentationDependencies.lanWebServerController" in mainActivity,
    )
  }

  @Test
  fun `共有Library追加はroute graphではなく専用capabilityから取得する`() {
    val incomingDependencies = File(
      repositoryRoot,
      "app/src/main/java/dev/terashima/yomitorirss/entry/IncomingIntentDependencies.kt",
    ).readText()
    val application = File(
      repositoryRoot,
      "app/src/main/java/dev/terashima/yomitorirss/YomitoriApplication.kt",
    ).readText()

    assertFalse(
      "external intent handling must not reach into Library route dependencies",
      "routeDependencies.library.addWebBook" in incomingDependencies,
    )
    assertTrue(
      "IncomingIntentDependencies must receive a narrow shared-library capability",
      "private val addSharedWebBookCapability: suspend" in incomingDependencies,
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
