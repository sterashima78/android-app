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
    val featureRoot = File(repositoryRoot, "app/src/main/java/dev/terashima/yomitorirss/feature")
    val unexpected = featureRoot.walkTopDown()
      .filter { it.isFile && it.extension == "kt" }
      .map { it.relativeTo(featureRoot).invariantSeparatorsPath }
      .toList()
    assertTrue("feature-owned source and app-shell adapters must not return to app/feature: $unexpected", unexpected.isEmpty())
  }

  @Test
  fun `app shell navigationはui ownershipに置く`() {
    val appSourceRoot = File(repositoryRoot, "app/src/main/java/dev/terashima/yomitorirss")
    val legacyReferences = appSourceRoot.walkTopDown()
      .filter { it.isFile && it.extension == "kt" }
      .filter { "dev.terashima.yomitorirss.feature.navigation" in it.readText() }
      .map { it.relativeTo(appSourceRoot).invariantSeparatorsPath }
      .toList()
    assertTrue("app-shell navigation must not use the historical feature.navigation package: $legacyReferences", legacyReferences.isEmpty())
    listOf("AppSection.kt", "AppViewModel.kt", "MainTab.kt").forEach { fileName ->
      assertTrue("app-shell navigation type must live under app/ui: $fileName", File(appSourceRoot, "ui/$fileName").isFile)
    }
  }

  @Test
  fun `AppContainerはfeature data implementationの直接構築を持たない`() {
    val source = File(repositoryRoot, "app/src/main/java/dev/terashima/yomitorirss/AppContainer.kt").readText()
    assertFalse(
      "AppContainer should delegate feature data construction to runtime dependency groups",
      Regex("(?m)^import dev\\.terashima\\.yomitorirss\\.feature\\..+\\.data\\.").containsMatchIn(source),
    )
  }

  @Test
  fun `application graphは単一HTTP transportをruntime groupへ渡す`() {
    val container = File(repositoryRoot, "app/src/main/java/dev/terashima/yomitorirss/AppContainer.kt").readText()
    val workerFactory = File(repositoryRoot, "app/src/main/java/dev/terashima/yomitorirss/AppWorkerFactory.kt").readText()
    val summaryFetchWorker = File(
      repositoryRoot,
      "feature/summary/data/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/data/SummaryContentFetchWorker.kt",
    ).readText()
    assertTrue("AppContainer must own the application HTTP transport", "internal val httpClient: HttpClient" in container)
    assertTrue("content, supporting and feature runtime groups must receive the shared transport", Regex("httpClient = httpClient").findAll(container).count() >= 3)
    assertTrue("background article fetch must receive the same application transport through WorkerFactory", "ArticleContentClient(container.httpClient)" in workerFactory)
    assertFalse("SummaryContentFetchWorker must not construct its own article HTTP adapter", "ArticleContentClient()" in summaryFetchWorker)
  }

  @Test
  fun `AppRouteDependenciesはLibrary BookReader Xのconcrete implementationを構築しない`() {
    val source = File(repositoryRoot, "app/src/main/java/dev/terashima/yomitorirss/AppRouteDependencies.kt").readText()
    val forbiddenImports = Regex("(?m)^import dev\\.terashima\\.yomitorirss\\.feature\\.(?:library|bookreader|x)\\.data\\.")
    val forbiddenConstructors = listOf(
      "GoogleBooksAuthorizationManager(",
      "WorkManagerSmbCoverPrefetchScheduler(",
      "SharedPreferencesSmbMetadataNormalizationPromptRepository(",
      "DefaultLibraryOrganizationSuggester(",
      "DefaultBookPageSourceFactory(",
      "SharedPreferencesReadingPositionStore(",
      "SharedPreferencesXViewerCssRepository(",
    )
    assertFalse("route wiring must delegate feature concrete graph construction to App runtime groups", forbiddenImports.containsMatchIn(source))
    forbiddenConstructors.forEach { constructor ->
      assertFalse("route wiring must not construct $constructor", constructor in source)
    }
  }

  @Test
  fun `Library app routeはplatform authorization compositionに限定する`() {
    val appRoute = File(repositoryRoot, "app/src/main/java/dev/terashima/yomitorirss/ui/LibraryRoute.kt").readText()
    val featureRoute = File(
      repositoryRoot,
      "feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/LibraryFeatureRoute.kt",
    ).readText()
    listOf("WebLibraryAddAction(", "WebLibrarySettingsUiBinding(", "SmbBookReaderRoute(", "mutableStateOf<LibraryBook?>", "collectAsState()").forEach { featureUiMarker ->
      assertFalse("app LibraryRoute must not own Library-specific UI state: $featureUiMarker", featureUiMarker in appRoute)
    }
    assertTrue("feature Library route must own Web Library add action", "WebLibraryAddAction(" in featureRoute)
    assertTrue("feature Library route must own Web Library settings state", "WebLibrarySettingsUiBinding(" in featureRoute)
    assertTrue("feature Library route must own SMB reader presentation", "SmbBookReaderRoute(" in featureRoute)
  }

  @Test
  fun `Integrated presentation ownershipはfeature moduleに置く`() {
    val appUiRoot = File(repositoryRoot, "app/src/main/java/dev/terashima/yomitorirss/ui")
    val integratedFeatureRoot = File(
      repositoryRoot,
      "feature/integrated/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/integrated/ui",
    )
    val ownedFiles = listOf(
      "IntegratedRoute.kt",
      "IntegratedProjection.kt",
      "IntegratedTargetDispatcher.kt",
      "IntegratedItemActions.kt",
    )
    ownedFiles.forEach { fileName ->
      assertFalse("Integrated feature implementation must not live in app/ui: $fileName", File(appUiRoot, fileName).isFile)
      assertTrue("Integrated feature must own $fileName", File(integratedFeatureRoot, fileName).isFile)
    }
    val projection = File(integratedFeatureRoot, "IntegratedProjection.kt").readText()
    assertFalse(
      "Integrated projection should remain a pure cross-feature mapper",
      Regex("(?m)^import (?:android\\.|androidx\\.compose\\.)").containsMatchIn(projection),
    )
  }

  @Test
  fun `app compositionはactive tab判定より前にfeature ViewModelを生成しない`() {
    assertNoViewModelBeforeDispatch(
      path = "app/src/main/java/dev/terashima/yomitorirss/ui/AppFeatureContent.kt",
      functionMarker = "internal fun AppFeatureContent(",
      dispatchMarker = "when (selectedTab)",
    )
    assertNoViewModelBeforeDispatch(
      path = "app/src/main/java/dev/terashima/yomitorirss/ui/AppTopBarRoute.kt",
      functionMarker = "internal fun AppTopBarRoute(",
      dispatchMarker = "when (selectedTab)",
    )
    assertNoViewModelBeforeDispatch(
      path = "app/src/main/java/dev/terashima/yomitorirss/ui/FeatureUiHosts.kt",
      functionMarker = "internal fun FeatureMessageEffects(",
      dispatchMarker = "val messageSources = selectedTab.featureMessageSources()",
    )
  }

  @Test
  fun `feature message effectsはnavigation capability policyを再定義しない`() {
    val source = File(repositoryRoot, "app/src/main/java/dev/terashima/yomitorirss/ui/FeatureUiHosts.kt").readText()
    assertTrue("FeatureMessageEffects must consume the centralized navigation capability mapping", "selectedTab.featureMessageSources()" in source)
    assertFalse("FeatureMessageEffects must not maintain a second selected-tab policy", "when (selectedTab)" in source)
  }

  @Test
  fun `runtime dependency group型はRouteとframework boundaryへ露出しない`() {
    val forbiddenTypes = listOf(
      "AppAiCoreRuntimeDependencies",
      "AppContentRuntimeDependencies",
      "AppCrossFeatureRuntimeDependencies",
      "AppFeatureRuntimeDependencies",
      "AppKnowledgeRuntimeDependencies",
      "AppSupportingRuntimeDependencies",
    )
    val boundaryFiles = buildList {
      addAll(
        File(repositoryRoot, "app/src/main/java/dev/terashima/yomitorirss/ui")
          .walkTopDown()
          .filter { it.isFile && it.extension == "kt" }
          .toList(),
      )
      add(File(repositoryRoot, "app/src/main/java/dev/terashima/yomitorirss/AppWorkerFactory.kt"))
      add(File(repositoryRoot, "app/src/main/java/dev/terashima/yomitorirss/MainActivityDependencies.kt"))
    }
    val unexpected = boundaryFiles.flatMap { file ->
      val source = file.readText()
      forbiddenTypes.filter { it in source }
        .map { type -> "${file.relativeTo(repositoryRoot).invariantSeparatorsPath}:$type" }
    }
    assertTrue("route/framework boundaries must consume narrow capabilities instead of runtime dependency groups: $unexpected", unexpected.isEmpty())
  }

  private fun assertNoViewModelBeforeDispatch(
    path: String,
    functionMarker: String,
    dispatchMarker: String,
  ) {
    val source = File(repositoryRoot, path).readText()
    val function = source.substringAfter(functionMarker)
    assertTrue("$path must contain active-tab dispatch marker: $dispatchMarker", dispatchMarker in function)
    val beforeDispatch = function.substringBefore(dispatchMarker)
    assertFalse(
      "$path must dispatch by selectedTab before resolving feature ViewModels",
      Regex("=\\s*viewModel\\(").containsMatchIn(beforeDispatch),
    )
  }
}
