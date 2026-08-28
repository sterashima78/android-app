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

  private val compositionSourceRoot = "app/composition/src/main/java/dev/terashima/yomitorirss"
  private val presentationUiRoot = "app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui"

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
  fun `app shell navigationはpresentation moduleが所有する`() {
    val appSourceRoot = File(
      repositoryRoot,
      "app/src/main/java/dev/terashima/yomitorirss",
    )
    val presentationRoot = File(repositoryRoot, presentationUiRoot)
    val legacyReferences = listOf(appSourceRoot, presentationRoot)
      .flatMap { sourceRoot ->
        sourceRoot
          .walkTopDown()
          .filter { it.isFile && it.extension == "kt" }
          .filter { "dev.terashima.yomitorirss.feature.navigation" in it.readText() }
          .map { it.relativeTo(repositoryRoot).invariantSeparatorsPath }
          .toList()
      }

    assertTrue(
      "app-shell navigation must not use the historical feature.navigation package: $legacyReferences",
      legacyReferences.isEmpty(),
    )
    listOf("AppSection.kt", "AppNavigationSpec.kt", "AppNavHost.kt").forEach { fileName ->
      assertTrue(
        "app-shell navigation implementation must live in :app:presentation: $fileName",
        File(presentationRoot, fileName).isFile,
      )
    }
    listOf("AppViewModel.kt", "MainTab.kt", "AppFeatureContent.kt").forEach { obsoleteFile ->
      assertFalse(
        "manual selected-tab navigation must not return: $obsoleteFile",
        File(presentationRoot, obsoleteFile).isFile,
      )
    }

    val mainActivity = File(appSourceRoot, "MainActivity.kt").readText()
    val yomitoriApp = File(presentationRoot, "YomitoriApp.kt").readText()
    val navHost = File(presentationRoot, "AppNavHost.kt").readText()
    val navOwnerIndex = mainActivity.indexOf("val navController = rememberNavController()")
    val lockDispatchIndex = mainActivity.indexOf("when {")
    assertTrue(
      "MainActivity root composition must keep the NavController above app-lock dispatch",
      navOwnerIndex >= 0 && lockDispatchIndex >= 0 && navOwnerIndex < lockDispatchIndex,
    )
    assertTrue(
      "MainActivity must pass the retained NavController into MainContent",
      "MainContent(navController)" in mainActivity,
    )
    assertFalse(
      "YomitoriApp must consume the retained NavController instead of recreating it",
      "rememberNavController()" in yomitoriApp,
    )
    assertTrue("AppNavHost must own the root Navigation Compose graph", "NavHost(" in navHost)
    assertFalse("YomitoriApp must not restore selected-tab state", "selectedTab" in yomitoriApp)
  }

  @Test
  fun `presentation moduleはfeature UIを隔離しdataへ依存しない`() {
    val settings = File(repositoryRoot, "settings.gradle.kts").readText()
    val appBuild = File(repositoryRoot, "app/build.gradle.kts").readText()
    val presentationBuild = File(repositoryRoot, "app/presentation/build.gradle.kts").readText()
    val executableUiRoot = File(repositoryRoot, "app/src/main/java/dev/terashima/yomitorirss/ui")

    assertTrue(":app:presentation must be a Gradle module", "include(\":app:presentation\")" in settings)
    assertTrue(
      ":app must depend on the presentation boundary",
      "implementation(project(\":app:presentation\"))" in appBuild,
    )
    assertFalse(
      ":app must not depend directly on feature UI modules",
      Regex("implementation\\(project\\(\":feature:[^\"]+:ui\"\\)\\)").containsMatchIn(appBuild),
    )
    assertTrue(
      ":app:presentation must compose feature UI modules",
      Regex("implementation\\(project\\(\":feature:[^\"]+:ui\"\\)\\)").containsMatchIn(presentationBuild),
    )
    assertFalse(
      ":app:presentation must not depend on feature data modules",
      Regex("implementation\\(project\\(\":feature:[^\"]+:data\"\\)\\)").containsMatchIn(presentationBuild),
    )
    assertFalse(
      ":app:presentation must not depend on executable :app",
      "project(\":app\")" in presentationBuild,
    )
    val executableUiFiles = executableUiRoot
      .walkTopDown()
      .filter { it.isFile && it.extension == "kt" }
      .map { it.relativeTo(repositoryRoot).invariantSeparatorsPath }
      .toList()
    assertTrue(
      "app-shell production UI must not remain in executable :app: $executableUiFiles",
      executableUiFiles.isEmpty(),
    )
  }

  @Test
  fun `YomitoriAppはfeature stateとActivity Resultを所有しない`() {
    val source = File(repositoryRoot, "$presentationUiRoot/YomitoriApp.kt").readText()
    val featurePresentationImport = Regex(
      "(?m)^\\s*import\\s+dev\\.terashima\\.yomitorirss\\.feature\\.[A-Za-z0-9_.]+\\.(?:[A-Za-z0-9_]*UiState|[A-Za-z0-9_]*Screen|[A-Za-z0-9_]*Dialog)(?:\\s+as\\s+[A-Za-z0-9_]+)?\\s*$",
    )
    val stateCollector = Regex(
      "\\b([A-Za-z_][A-Za-z0-9_]*)\\.state\\.collectAsState(?:WithLifecycle)?\\s*\\(",
    )

    assertFalse("YomitoriApp must not import feature-owned presentation state/screens", featurePresentationImport.containsMatchIn(source))
    assertFalse("YomitoriApp must not use feature wildcard imports", Regex("(?m)^\\s*import\\s+dev\\.terashima\\.yomitorirss\\.feature\\.[A-Za-z0-9_.]+\\.\\*\\s*$").containsMatchIn(source))
    val unexpectedCollectors = stateCollector.findAll(source)
      .map { it.groupValues[1] }
      .filter { it != "appViewModel" }
      .toList()
    assertTrue("YomitoriApp must not collect feature ViewModel state: $unexpectedCollectors", unexpectedCollectors.isEmpty())
    assertFalse("YomitoriApp must not own Activity Result launchers", "rememberLauncherForActivityResult" in source)
    assertFalse("YomitoriApp must not own Activity Result contracts", "ActivityResultContracts." in source)
  }

  @Test
  fun `feature UI moduleがdestination route contractを所有する`() {
    val destinationFiles = listOf(
      "feature/integrated/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/integrated/ui/NavigationDestination.kt",
      "feature/rss/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/NavigationDestination.kt",
      "feature/reddit/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/reddit/NavigationDestination.kt",
      "feature/bookmark/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/bookmark/NavigationDestination.kt",
      "feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/NavigationDestination.kt",
      "feature/mail/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/mail/NavigationDestination.kt",
      "feature/task/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/task/NavigationDestination.kt",
      "feature/settings/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/NavigationDestination.kt",
    )

    destinationFiles.forEach { path ->
      assertTrue("feature must own navigation destination contract: $path", File(repositoryRoot, path).isFile)
    }
  }

  @Test
  fun `app moduleはfeature dataへ直接依存しない`() {
    val appBuild = File(repositoryRoot, "app/build.gradle.kts").readText()
    val directDataDependencies = Regex(
      "implementation\\(project\\(\":feature:[^\"]+:data\"\\)\\)",
    ).findAll(appBuild).map { it.value }.toList()

    assertTrue(
      ":app must not have direct feature data dependencies: $directDataDependencies",
      directDataDependencies.isEmpty(),
    )
    assertTrue(
      ":app must depend on the dedicated composition boundary",
      "implementation(project(\":app:composition\"))" in appBuild,
    )
  }

  @Test
  fun `app production sourceはfeature data implementationをimportしない`() {
    val appSourceRoot = File(repositoryRoot, "app/src/main/java")
    val unexpected = appSourceRoot
      .walkTopDown()
      .filter { it.isFile && it.extension == "kt" }
      .filter {
        Regex("(?m)^import dev\\.terashima\\.yomitorirss\\.feature\\..+\\.data(?:\\.|$)")
          .containsMatchIn(it.readText())
      }
      .map { it.relativeTo(repositoryRoot).invariantSeparatorsPath }
      .toList()

    assertTrue(
      ":app source must not compile against feature data implementations: $unexpected",
      unexpected.isEmpty(),
    )
  }

  @Test
  fun `composition moduleがfeature data dependencyを所有する`() {
    val settings = File(repositoryRoot, "settings.gradle.kts").readText()
    val compositionBuild = File(repositoryRoot, "app/composition/build.gradle.kts").readText()

    assertTrue(":app:composition must be a Gradle module", "include(\":app:composition\")" in settings)
    assertTrue(
      "composition boundary must own concrete feature data dependencies",
      Regex("implementation\\(project\\(\":feature:[^\"]+:data\"\\)\\)").containsMatchIn(compositionBuild),
    )
  }

  @Test
  fun `AppContainerはfeature data implementationの直接構築を持たない`() {
    val source = File(repositoryRoot, "$compositionSourceRoot/AppContainer.kt").readText()

    assertFalse(
      "AppContainer should delegate feature data construction to runtime dependency groups",
      Regex("(?m)^import dev\\.terashima\\.yomitorirss\\.feature\\..+\\.data\\.").containsMatchIn(source),
    )
  }

  @Test
  fun `application graphは単一HTTP transportをruntime groupへ渡す`() {
    val container = File(repositoryRoot, "$compositionSourceRoot/AppContainer.kt").readText()
    val workerFactory = File(repositoryRoot, "$compositionSourceRoot/AppWorkerFactory.kt").readText()
    val summaryFetchWorker = File(
      repositoryRoot,
      "feature/summary/data/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/data/SummaryContentFetchWorker.kt",
    ).readText()

    assertTrue("AppContainer must own the application HTTP transport", "internal val httpClient: HttpClient" in container)
    assertTrue(
      "content, supporting and feature runtime groups must receive the shared transport",
      Regex("httpClient = httpClient").findAll(container).count() >= 3,
    )
    assertTrue(
      "background article fetch must receive the same application transport through WorkerFactory",
      "ArticleContentClient(container.httpClient)" in workerFactory,
    )
    assertFalse(
      "SummaryContentFetchWorker must not construct its own article HTTP adapter",
      "ArticleContentClient()" in summaryFetchWorker,
    )
  }

  @Test
  fun `AppRouteDependenciesはLibrary BookReader Xのconcrete implementationを構築しない`() {
    val source = File(repositoryRoot, "$compositionSourceRoot/AppRouteDependencies.kt").readText()
    val forbiddenImports = Regex(
      "(?m)^import dev\\.terashima\\.yomitorirss\\.feature\\.(?:library|bookreader|x)\\.data\\.",
    )
    val forbiddenConstructors = listOf(
      "GoogleBooksAuthorizationManager(",
      "WorkManagerSmbCoverPrefetchScheduler(",
      "SharedPreferencesSmbMetadataNormalizationPromptRepository(",
      "DefaultLibraryOrganizationSuggester(",
      "DefaultBookPageSourceFactory(",
      "SharedPreferencesReadingPositionStore(",
      "SharedPreferencesXViewerCssRepository(",
    )

    assertFalse(
      "route wiring must delegate feature concrete graph construction to App runtime groups",
      forbiddenImports.containsMatchIn(source),
    )
    forbiddenConstructors.forEach { constructor ->
      assertFalse("route wiring must not construct $constructor", constructor in source)
    }
  }

  @Test
  fun `Library app routeはplatform authorization compositionに限定する`() {
    val appRoute = File(repositoryRoot, "$presentationUiRoot/LibraryRoute.kt").readText()
    val featureRoute = File(
      repositoryRoot,
      "feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/LibraryFeatureRoute.kt",
    ).readText()

    listOf(
      "WebLibraryAddAction(",
      "WebLibrarySettingsUiBinding(",
      "SmbBookReaderRoute(",
      "mutableStateOf<LibraryBook?>",
      "collectAsState()",
    ).forEach { featureUiMarker ->
      assertFalse(
        "app LibraryRoute must not own Library-specific UI state: $featureUiMarker",
        featureUiMarker in appRoute,
      )
    }
    assertTrue("feature Library route must own Web Library add action", "WebLibraryAddAction(" in featureRoute)
    assertTrue("feature Library route must own Web Library settings state", "WebLibrarySettingsUiBinding(" in featureRoute)
    assertTrue("feature Library route must own SMB reader presentation", "SmbBookReaderRoute(" in featureRoute)
  }

  @Test
  fun `Integrated presentation ownershipはfeature moduleに置く`() {
    val appUiRoot = File(repositoryRoot, presentationUiRoot)
    val featureRoot = File(
      repositoryRoot,
      "feature/integrated/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/integrated/ui",
    )
    listOf(
      "IntegratedRoute.kt",
      "IntegratedProjection.kt",
      "IntegratedTargetDispatcher.kt",
      "IntegratedItemActions.kt",
    ).forEach { fileName ->
      assertFalse("Integrated feature implementation must not live in app presentation: $fileName", File(appUiRoot, fileName).isFile)
      assertTrue("Integrated feature must own $fileName", File(featureRoot, fileName).isFile)
    }

    val projection = File(featureRoot, "IntegratedProjection.kt").readText()
    assertFalse(
      "Integrated projection should remain a pure cross-feature mapper",
      Regex("(?m)^import (?:android\\.|androidx\\.compose\\.)").containsMatchIn(projection),
    )
  }

  @Test
  fun `app compositionはnavigation destination判定より前にfeature ViewModelを生成しない`() {
    assertNoViewModelBeforeDispatch(
      path = "$presentationUiRoot/AppNavHost.kt",
      functionMarker = "internal fun AppNavHost(",
      dispatchMarker = "NavHost(",
    )
    assertNoViewModelBeforeDispatch(
      path = "$presentationUiRoot/AppTopBarRoute.kt",
      functionMarker = "internal fun AppTopBarRoute(",
      dispatchMarker = "when (selectedRoute)",
    )
    assertNoViewModelBeforeDispatch(
      path = "$presentationUiRoot/FeatureUiHosts.kt",
      functionMarker = "internal fun FeatureMessageEffects(",
      dispatchMarker = "val messageSources = selectedRoute.featureMessageSources()",
    )
  }

  @Test
  fun `feature message effectsはnavigation capability policyを再定義しない`() {
    val source = File(repositoryRoot, "$presentationUiRoot/FeatureUiHosts.kt").readText()

    assertTrue(
      "FeatureMessageEffects must consume the centralized navigation capability mapping",
      "selectedRoute.featureMessageSources()" in source,
    )
    assertFalse(
      "FeatureMessageEffects must not maintain a second selected-route policy",
      "when (selectedRoute)" in source,
    )
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
        File(repositoryRoot, presentationUiRoot)
          .walkTopDown()
          .filter { it.isFile && it.extension == "kt" }
          .toList(),
      )
      add(File(repositoryRoot, "$compositionSourceRoot/AppWorkerFactory.kt"))
      add(File(repositoryRoot, "app/src/main/java/dev/terashima/yomitorirss/MainActivityDependencies.kt"))
    }

    val unexpected = boundaryFiles.flatMap { file ->
      val source = file.readText()
      forbiddenTypes
        .filter { it in source }
        .map { type -> "${file.relativeTo(repositoryRoot).invariantSeparatorsPath}:$type" }
    }

    assertTrue(
      "route/framework boundaries must consume narrow capabilities instead of runtime dependency groups: $unexpected",
      unexpected.isEmpty(),
    )
  }

  private fun assertNoViewModelBeforeDispatch(
    path: String,
    functionMarker: String,
    dispatchMarker: String,
  ) {
    val source = File(repositoryRoot, path).readText()
    val function = source.substringAfter(functionMarker)
    assertTrue("$path must contain navigation dispatch marker: $dispatchMarker", dispatchMarker in function)
    val beforeDispatch = function.substringBefore(dispatchMarker)
    assertFalse(
      "$path must dispatch by active navigation destination before resolving feature ViewModels",
      Regex("=\\s*viewModel\\(").containsMatchIn(beforeDispatch),
    )
  }
}
