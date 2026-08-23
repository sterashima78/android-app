package dev.terashima.yomitorirss

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCompositionSourceArchitectureTest {
  private val repositoryRoot: File by lazy {
    generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
      .firstOrNull { File(it, "settings.gradle.kts").isFile && File(it, "app").isDirectory }
      ?: error("repository root not found")
  }

  @Test
  fun `app feature namespaceにはproduction sourceを置かない`() {
    val featureRoot = File(
      repositoryRoot,
      "app/src/main/java/dev/terashima/yomitorirss/feature",
    )
    val unexpected = featureRoot
      .walkTopDown()
      .filter { it.isFile && it.extension == "kt" }
      .map { it.relativeTo(featureRoot).invariantSeparatorsPath }
      .toList()

    assertTrue(
      "feature-owned source and app-shell adapters must not return to app/feature: $unexpected",
      unexpected.isEmpty(),
    )
  }

  @Test
  fun `app shell navigationはui ownershipに置く`() {
    val appSourceRoot = File(
      repositoryRoot,
      "app/src/main/java/dev/terashima/yomitorirss",
    )
    val legacyReferences = appSourceRoot
      .walkTopDown()
      .filter { it.isFile && it.extension == "kt" }
      .filter { "dev.terashima.yomitorirss.feature.navigation" in it.readText() }
      .map { it.relativeTo(appSourceRoot).invariantSeparatorsPath }
      .toList()

    assertTrue(
      "app-shell navigation must not use the historical feature.navigation package: $legacyReferences",
      legacyReferences.isEmpty(),
    )
    listOf("AppSection.kt", "AppViewModel.kt", "MainTab.kt").forEach { fileName ->
      assertTrue(
        "app-shell navigation type must live under app/ui: $fileName",
        File(appSourceRoot, "ui/$fileName").isFile,
      )
    }
  }

  @Test
  fun `AppContainerはfeature data implementationの直接構築を持たない`() {
    val source = File(
      repositoryRoot,
      "app/src/main/java/dev/terashima/yomitorirss/AppContainer.kt",
    ).readText()

    assertFalse(
      "AppContainer should delegate feature data construction to runtime dependency groups",
      Regex("(?m)^import dev\\.terashima\\.yomitorirss\\.feature\\..+\\.data\\.").containsMatchIn(source),
    )
  }

  @Test
  fun `AppRouteDependenciesはLibrary BookReader Xのconcrete implementationを構築しない`() {
    val source = File(
      repositoryRoot,
      "app/src/main/java/dev/terashima/yomitorirss/AppRouteDependencies.kt",
    ).readText()
    val forbiddenImports = Regex(
      "(?m)^import dev\\.terashima\\.yomitorirss\\.feature\\.(?:library|bookreader|x)\\.data\\.",
    )
    val forbiddenConstructors = listOf(
      "GoogleBooksAuthorizationManager(",
      "WorkManagerSmbCoverPrefetchScheduler(",
      "SharedPreferencesSmbMetadataNormalizationPromptRepository(",
      "LocalLibraryOrganizationSuggester(",
      "DefaultBookPageSourceFactory(",
      "SharedPreferencesReadingPositionStore(",
      "SharedPreferencesXViewerCssRepository(",
    )

    assertFalse(
      "route wiring must delegate feature concrete graph construction to App runtime groups",
      forbiddenImports.containsMatchIn(source),
    )
    forbiddenConstructors.forEach { constructor ->
      assertFalse(
        "route wiring must not construct $constructor",
        constructor in source,
      )
    }
  }

  @Test
  fun `Library app routeはplatform authorization compositionに限定する`() {
    val appRoute = File(
      repositoryRoot,
      "app/src/main/java/dev/terashima/yomitorirss/ui/LibraryRoute.kt",
    ).readText()
    val featureRoute = File(
      repositoryRoot,
      "feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/LibraryFeatureRoute.kt",
    ).readText()

    listOf(
      "WebLibraryActions(",
      "SmbBookReaderRoute(",
      "mutableStateOf<LibraryBook?>",
      "collectAsState()",
    ).forEach { featureUiMarker ->
      assertFalse(
        "app LibraryRoute must not own Library-specific UI state: $featureUiMarker",
        featureUiMarker in appRoute,
      )
    }
    assertTrue("feature Library route must own Web Library actions", "WebLibraryActions(" in featureRoute)
    assertTrue("feature Library route must own SMB reader presentation", "SmbBookReaderRoute(" in featureRoute)
  }

  @Test
  fun `Integrated projectionはComposeとAndroid frameworkに依存しない`() {
    val source = File(
      repositoryRoot,
      "app/src/main/java/dev/terashima/yomitorirss/ui/IntegratedProjection.kt",
    ).readText()

    assertFalse(
      "Integrated projection should remain a pure cross-feature mapper",
      Regex("(?m)^import (?:android\\.|androidx\\.compose\\.)").containsMatchIn(source),
    )
  }

  @Test
  fun `app compositionはactive tab判定より前にfeature ViewModelを生成しない`() {
    assertNoViewModelBeforeTabDispatch(
      path = "app/src/main/java/dev/terashima/yomitorirss/ui/AppFeatureContent.kt",
      functionMarker = "internal fun AppFeatureContent(",
    )
    assertNoViewModelBeforeTabDispatch(
      path = "app/src/main/java/dev/terashima/yomitorirss/ui/AppTopBarRoute.kt",
      functionMarker = "internal fun AppTopBarRoute(",
    )
    assertNoViewModelBeforeTabDispatch(
      path = "app/src/main/java/dev/terashima/yomitorirss/ui/FeatureUiHosts.kt",
      functionMarker = "internal fun FeatureMessageEffects(",
    )
  }

  @Test
  fun `RSS content tabsはFeedViewModelのmessageを表示して消費する`() {
    val source = File(
      repositoryRoot,
      "app/src/main/java/dev/terashima/yomitorirss/ui/FeatureUiHosts.kt",
    ).readText()
    val rssContentBranch = source
      .substringAfter("MainTab.UNREAD,")
      .substringAfter("MainTab.READ_LATER -> {")
      .substringBefore("MainTab.FEEDS -> {")

    assertTrue(
      "UNREAD/READ_LATER must observe FeedViewModel state for refresh completion/error messages",
      rssContentBranch.contains(
        "val feedState by feedViewModel.state.collectAsState()",
      ),
    )
    assertTrue(
      "UNREAD/READ_LATER must consume FeedViewModel messages on the active RSS tab",
      rssContentBranch.contains(
        "FeatureMessageEffect(feedState.message, snackbarHostState, feedViewModel::dismissMessage)",
      ),
    )
  }

  private fun assertNoViewModelBeforeTabDispatch(path: String, functionMarker: String) {
    val source = File(repositoryRoot, path).readText()
    val function = source.substringAfter(functionMarker)
    val beforeDispatch = function.substringBefore("when (selectedTab)")
    assertFalse(
      "$path must dispatch by selectedTab before resolving feature ViewModels",
      Regex("=\\s*viewModel\\(").containsMatchIn(beforeDispatch),
    )
  }
}
