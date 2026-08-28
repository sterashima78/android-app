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
  fun `MainActivity dependencyはpresentationとLAN Webとincoming intentのfacadeに分離する`() {
    val legacyDependencies = File(
      repositoryRoot,
      "app/src/main/java/dev/terashima/yomitorirss/MainActivityDependencies.kt",
    )
    val provider = source("app/src/main/java/dev/terashima/yomitorirss/MainActivityDependenciesProvider.kt")
    val presentationDependencies = source(
      "app/src/main/java/dev/terashima/yomitorirss/MainActivityPresentationDependencies.kt",
    )
    val lanWebDependencies = source(
      "app/src/main/java/dev/terashima/yomitorirss/MainActivityLanWebDependencies.kt",
    )
    val incomingDependencies = source(
      "app/src/main/java/dev/terashima/yomitorirss/entry/IncomingIntentDependencies.kt",
    )
    val mainActivity = source("app/src/main/java/dev/terashima/yomitorirss/MainActivity.kt")

    assertFalse("combined MainActivity dependency facade must not return", legacyDependencies.exists())
    assertTrue(
      "framework provider must expose the presentation facade",
      "val mainActivityPresentationDependencies: MainActivityPresentationDependencies" in provider,
    )
    assertTrue(
      "framework provider must expose the LAN Web facade",
      "val mainActivityLanWebDependencies: MainActivityLanWebDependencies" in provider,
    )
    assertTrue(
      "framework provider must expose the incoming intent facade",
      "val incomingIntentDependencies: IncomingIntentDependencies" in provider,
    )
    assertTrue(
      "presentation facade must own route composition",
      "val routeDependencies: AppRouteDependencies" in presentationDependencies,
    )
    assertFalse(
      "presentation facade must not own LAN Web or external Intent capabilities",
      "LanWebServerController" in presentationDependencies ||
        "SharedContentEntryCapability" in presentationDependencies,
    )
    assertTrue(
      "LAN Web facade must own only the server controller connection",
      "val controller: LanWebServerController" in lanWebDependencies,
    )
    assertFalse(
      "LAN Web facade must not own route or external Intent capabilities",
      "AppRouteDependencies" in lanWebDependencies ||
        "SharedContentEntryCapability" in lanWebDependencies,
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
      "presentationDependencies.routeDependencies" in mainActivity,
    )
    assertTrue(
      "MainActivity LAN Web host must use only the LAN Web facade",
      "lanWebDependencies.controller" in mainActivity,
    )
  }

  @Test
  fun `記事open callbackはexecutable境界でURLへ縮約する`() {
    val mainActivity = source("app/src/main/java/dev/terashima/yomitorirss/MainActivity.kt")
    val yomitoriApp = source(
      "app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/YomitoriApp.kt",
    )

    assertFalse(
      "executable MainActivity must not compile against Article domain type",
      "feature.article.Article" in mainActivity,
    )
    assertTrue(
      "MainActivity must receive an URL-only article callback",
      "onOpenArticleUrl = ::openArticleUrl" in mainActivity,
    )
    assertTrue(
      "presentation must adapt the feature Article to its executable URL capability",
      "onOpenArticle = { article -> onOpenArticleUrl(article.url) }" in yomitoriApp,
    )
  }

  @Test
  fun `共有Intent mutationはfeature neutral capabilityを利用する`() {
    val incomingDependencies = source(
      "app/src/main/java/dev/terashima/yomitorirss/entry/IncomingIntentDependencies.kt",
    )
    val application = source("app/src/main/java/dev/terashima/yomitorirss/YomitoriApplication.kt")
    val capability = source(
      "app/composition/src/main/java/dev/terashima/yomitorirss/composition/entry/SharedContentEntryCapability.kt",
    )

    assertTrue(
      "IncomingIntentDependencies must depend on the feature-neutral shared-content capability",
      "SharedContentEntryCapability" in incomingDependencies,
    )
    assertFalse(
      "executable incoming Intent facade must not expose Bookmark domain types",
      "feature.bookmark" in incomingDependencies,
    )
    assertFalse(
      "executable incoming Intent facade must not expose Library domain types",
      "feature.library" in incomingDependencies,
    )
    assertTrue(
      "Application composition must wire the neutral shared-content capability",
      "sharedContentEntry = container.sharedContentEntryCapability" in application,
    )
    assertTrue(
      "composition must translate Bookmark result semantics",
      "BookmarkSaveResult.ADDED -> SharedBookmarkSaveOutcome.ADDED" in capability,
    )
    assertTrue(
      "composition must narrow Library mutation output to the added title",
      "AddedSharedWebBook(" in capability,
    )
  }

  private fun source(path: String): String = File(repositoryRoot, path).readText()
}
