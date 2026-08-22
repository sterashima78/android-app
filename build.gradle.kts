import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.GradleException
import org.gradle.api.artifacts.ProjectDependency

plugins {
  id("com.android.application") version "9.3.0" apply false
  id("com.android.library") version "9.3.0" apply false
  id("org.jetbrains.kotlin.jvm") version "2.4.10" apply false
  id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
}

data class ProjectDependencyEdge(
  val source: String,
  val configuration: String,
  val target: String,
)

val baseArchitectureDependencyConfigurations =
  setOf(
    "api",
    "implementation",
    "compileOnly",
    "compileOnlyApi",
    "runtimeOnly",
  )

val variantArchitectureDependencyConfigurationSuffixes =
  setOf(
    "Api",
    "Implementation",
    "CompileOnly",
    "CompileOnlyApi",
    "RuntimeOnly",
  )

val appFeatureWorkerExceptions = mapOf(
  "app/src/main/java/dev/terashima/yomitorirss/BookmarkAutoEnrichmentBackfillWorker.kt" to
    "ADR-0101: legacy WorkManager FQCN compatibility shim",
  "app/src/main/java/dev/terashima/yomitorirss/feature/knowledge/KnowledgeWorkerCompat.kt" to
    "ADR-0101: legacy WorkManager FQCN compatibility shim",
)

fun isArchitectureDependencyConfiguration(name: String): Boolean {
  if ("test" in name.lowercase()) return false

  return name in baseArchitectureDependencyConfigurations ||
    variantArchitectureDependencyConfigurationSuffixes.any { suffix -> name.endsWith(suffix) }
}

fun projectLayer(path: String): String = path.substringAfterLast(':')

fun sourceArchitectureViolations(
  projectPath: String,
  repositoryPath: String,
  sourceText: String,
): List<String> {
  val violations = mutableListOf<String>()
  val normalizedPath = repositoryPath.replace('\\', '/')
  val fileName = normalizedPath.substringAfterLast('/')

  val isYomitoriApp = projectPath == ":app" &&
    normalizedPath.endsWith("/dev/terashima/yomitorirss/ui/YomitoriApp.kt")
  if (isYomitoriApp) {
    val featurePresentationImport = Regex(
      """(?m)^\s*import\s+dev\.terashima\.yomitorirss\.feature\.[A-Za-z0-9_.]+\.(?:[A-Za-z0-9_]*UiState|[A-Za-z0-9_]*Screen|[A-Za-z0-9_]*Dialog)(?:\s+as\s+[A-Za-z0-9_]+)?\s*$""",
    )
    if (featurePresentationImport.containsMatchIn(sourceText)) {
      violations +=
        "YomitoriApp must not import feature-owned UiState/Screen/Dialog: $normalizedPath"
    }

    val featureWildcardImport = Regex(
      """(?m)^\s*import\s+dev\.terashima\.yomitorirss\.feature\.[A-Za-z0-9_.]+\.\*\s*$""",
    )
    if (featureWildcardImport.containsMatchIn(sourceText)) {
      violations +=
        "YomitoriApp must not use feature wildcard imports: $normalizedPath"
    }

    val stateCollectorPattern = Regex(
      """\b([A-Za-z_][A-Za-z0-9_]*)\.state\.collectAsState(?:WithLifecycle)?\s*\(""",
    )
    stateCollectorPattern.findAll(sourceText).forEach { match ->
      val owner = match.groupValues[1]
      if (owner != "appViewModel") {
        violations +=
          "YomitoriApp must not collect feature ViewModel state: $normalizedPath ($owner.state)"
      }
    }

    if (
      sourceText.contains("rememberLauncherForActivityResult") ||
      sourceText.contains("ActivityResultContracts.")
    ) {
      violations +=
        "YomitoriApp must not own feature Activity Result launchers: $normalizedPath"
    }
  }

  val isAppUiAdapter = projectPath == ":app" &&
    normalizedPath.startsWith("app/src/main/") &&
    "/ui/" in normalizedPath &&
    (fileName.endsWith("Adapter.kt") || fileName.endsWith("Adapters.kt"))
  if (isAppUiAdapter) {
    val appServiceLocatorReference = Regex(
      """(?:\bYomitoriApplication\b|\bAppContainer\b|\.container\b)""",
    )
    if (appServiceLocatorReference.containsMatchIn(sourceText)) {
      violations +=
        "app UI adapter must not use app container/service locator for feature dependencies: $normalizedPath"
    }
  }

  val isAppRoute = projectPath == ":app" &&
    normalizedPath.startsWith("app/src/main/") &&
    fileName.endsWith("Route.kt")
  if (fileName.endsWith("Screen.kt") || isAppRoute) {
    val concreteFeatureDataImport = Regex(
      """(?m)^\s*import\s+dev\.terashima\.yomitorirss\.feature\.[A-Za-z0-9_.]+\.data\.""",
    )
    if (concreteFeatureDataImport.containsMatchIn(sourceText)) {
      violations +=
        "Screen/Route must not import concrete feature data implementations: $normalizedPath"
    }

    val infrastructureImport = Regex(
      """(?m)^\s*import\s+(?:dev\.terashima\.yomitorirss\.core\.database\.(?:DatabaseConnection|YomitoriDatabase)\b|androidx\.work\.)""",
    )
    if (infrastructureImport.containsMatchIn(sourceText)) {
      violations +=
        "Screen/Route must not import database or WorkManager infrastructure: $normalizedPath"
    }

    val concreteConstruction = Regex(
      """\b(?:DatabaseConnection|YomitoriDatabase|Default[A-Za-z0-9_]*Repository|WorkManager[A-Za-z0-9_]*(?:Scheduler|Controller))\s*\(""",
    )
    concreteConstruction.find(sourceText)?.let { match ->
      violations +=
        "Screen/Route must not construct concrete data/background dependencies: $normalizedPath (${match.value.trim()})"
    }
  }

  val isAppProductionSource = projectPath == ":app" &&
    normalizedPath.startsWith("app/src/main/")
  val workerDeclaration = Regex(
    """:\s*(?:androidx\.work\.)?(?:CoroutineWorker|Worker|ListenableWorker)\s*\(""",
  )
  if (
    isAppProductionSource &&
    workerDeclaration.containsMatchIn(sourceText) &&
    normalizedPath !in appFeatureWorkerExceptions
  ) {
    violations +=
      "feature-specific Worker runtime must live in the owning feature data module: $normalizedPath"
  }

  if (projectPath.startsWith(":feature:") && projectLayer(projectPath) == "data") {
    val appImplementationReference = Regex(
      """(?:import\s+dev\.terashima\.yomitorirss\.(?:YomitoriApplication|AppContainer|MainActivity)\b|dev\.terashima\.yomitorirss\.(?:YomitoriApplication|AppContainer|MainActivity)\b)""",
    )
    if (appImplementationReference.containsMatchIn(sourceText)) {
      violations +=
        "feature data must depend on contracts instead of app implementation types: $normalizedPath"
    }
  }

  return violations
}

val verifyArchitectureRuleTests by tasks.registering {
  group = "verification"
  description = "Runs regression fixtures for source-level architecture ownership rules."

  doLast {
    fun assertViolation(
      name: String,
      projectPath: String,
      repositoryPath: String,
      sourceText: String,
      expectedMessage: String,
    ) {
      val actual = sourceArchitectureViolations(projectPath, repositoryPath, sourceText)
      if (actual.none { expectedMessage in it }) {
        throw GradleException(
          "Architecture rule fixture failed: $name expected '$expectedMessage' but got $actual",
        )
      }
    }

    fun assertClean(
      name: String,
      projectPath: String,
      repositoryPath: String,
      sourceText: String,
    ) {
      val actual = sourceArchitectureViolations(projectPath, repositoryPath, sourceText)
      if (actual.isNotEmpty()) {
        throw GradleException("Architecture rule fixture failed: $name expected no violations but got $actual")
      }
    }

    val yomitoriAppPath =
      "app/src/main/java/dev/terashima/yomitorirss/ui/YomitoriApp.kt"
    assertViolation(
      name = "YomitoriApp feature state collection",
      projectPath = ":app",
      repositoryPath = yomitoriAppPath,
      sourceText = "val rssState by rssViewModel.state.collectAsState()",
      expectedMessage = "YomitoriApp must not collect feature ViewModel state",
    )
    assertViolation(
      name = "YomitoriApp feature presentation import",
      projectPath = ":app",
      repositoryPath = yomitoriAppPath,
      sourceText = "import dev.terashima.yomitorirss.feature.rss.RssScreen",
      expectedMessage = "YomitoriApp must not import feature-owned UiState/Screen/Dialog",
    )
    assertViolation(
      name = "YomitoriApp Activity Result ownership",
      projectPath = ":app",
      repositoryPath = yomitoriAppPath,
      sourceText = "val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {}",
      expectedMessage = "YomitoriApp must not own feature Activity Result launchers",
    )
    assertClean(
      name = "YomitoriApp app navigation state",
      projectPath = ":app",
      repositoryPath = yomitoriAppPath,
      sourceText = "val appState by appViewModel.state.collectAsState()",
    )

    val featureUiAdaptersPath =
      "app/src/main/java/dev/terashima/yomitorirss/ui/FeatureUiAdapters.kt"
    assertViolation(
      name = "app UI adapter service locator",
      projectPath = ":app",
      repositoryPath = featureUiAdaptersPath,
      sourceText = "val repository = (LocalContext.current.applicationContext as YomitoriApplication).container.aiModelRepository",
      expectedMessage = "app UI adapter must not use app container/service locator",
    )
    assertClean(
      name = "app UI adapter presentation bridge",
      projectPath = ":app",
      repositoryPath = featureUiAdaptersPath,
      sourceText = "fun SummaryPromptDialog() = FeatureSummaryPromptDialog()",
    )

    val taskQueueScreenPath =
      "app/src/main/java/dev/terashima/yomitorirss/feature/settings/TaskQueueScreen.kt"
    assertViolation(
      name = "Screen concrete data import",
      projectPath = ":app",
      repositoryPath = taskQueueScreenPath,
      sourceText = "import dev.terashima.yomitorirss.feature.library.data.DefaultLibraryRepository",
      expectedMessage = "Screen/Route must not import concrete feature data implementations",
    )
    assertViolation(
      name = "Screen concrete dependency construction",
      projectPath = ":app",
      repositoryPath = taskQueueScreenPath,
      sourceText = "val repository = DefaultLibraryRepository(DatabaseConnection(database))",
      expectedMessage = "Screen/Route must not construct concrete data/background dependencies",
    )
    assertViolation(
      name = "Route concrete dependency construction",
      projectPath = ":app",
      repositoryPath = "app/src/main/java/dev/terashima/yomitorirss/feature/library/LibraryRoute.kt",
      sourceText = "val repository = DefaultLibraryRepository(DatabaseConnection(database))",
      expectedMessage = "Screen/Route must not construct concrete data/background dependencies",
    )

    assertViolation(
      name = "feature Worker in app",
      projectPath = ":app",
      repositoryPath = "app/src/main/java/dev/terashima/yomitorirss/feature/knowledge/NewWorker.kt",
      sourceText = "class NewWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params)",
      expectedMessage = "feature-specific Worker runtime must live in the owning feature data module",
    )
    assertViolation(
      name = "root package Worker in app",
      projectPath = ":app",
      repositoryPath = "app/src/main/java/dev/terashima/yomitorirss/BookmarkBackfillWorker.kt",
      sourceText = "class BookmarkBackfillWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params)",
      expectedMessage = "feature-specific Worker runtime must live in the owning feature data module",
    )
    assertClean(
      name = "legacy bookmark backfill Worker compatibility shim",
      projectPath = ":app",
      repositoryPath = "app/src/main/java/dev/terashima/yomitorirss/BookmarkAutoEnrichmentBackfillWorker.kt",
      sourceText = "class BookmarkAutoEnrichmentBackfillWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params)",
    )
    assertClean(
      name = "legacy Knowledge Worker compatibility shim",
      projectPath = ":app",
      repositoryPath = "app/src/main/java/dev/terashima/yomitorirss/feature/knowledge/KnowledgeWorkerCompat.kt",
      sourceText = "class KnowledgeBuildWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params)",
    )
    assertViolation(
      name = "feature data imports app implementation",
      projectPath = ":feature:knowledge:data",
      repositoryPath =
        "feature/knowledge/data/src/main/kotlin/dev/terashima/yomitorirss/feature/knowledge/data/KnowledgeBuildBackground.kt",
      sourceText = "import dev.terashima.yomitorirss.YomitoriApplication",
      expectedMessage = "feature data must depend on contracts instead of app implementation types",
    )
  }
}

val verifyArchitecture by tasks.registering {
  group = "verification"
  description = "Verifies Gradle dependency rules and production source ownership/layout defined by the architecture ADRs."
  dependsOn(verifyArchitectureRuleTests)

  doLast {
    val edges =
      subprojects
        .flatMap { sourceProject ->
          sourceProject.configurations
            .filter { isArchitectureDependencyConfiguration(it.name) }
            .flatMap { configuration ->
              configuration.dependencies
                .withType(ProjectDependency::class.java)
                .map { dependency ->
                  ProjectDependencyEdge(
                    source = sourceProject.path,
                    configuration = configuration.name,
                    target = dependency.path,
                  )
                }
            }
        }
        .distinct()

    val violations = mutableListOf<String>()

    edges.forEach { edge ->
      val sourceLayer = projectLayer(edge.source)
      val targetLayer = projectLayer(edge.target)

      if (edge.source.startsWith(":core:") && edge.target.startsWith(":feature:")) {
        violations +=
          "core must not depend on feature: ${edge.source} --${edge.configuration}--> ${edge.target}"
      }

      if (sourceLayer == "domain" && targetLayer in setOf("ui", "data")) {
        violations +=
          "domain must not depend on ui/data: ${edge.source} --${edge.configuration}--> ${edge.target}"
      }

      if (
        sourceLayer == "ui" &&
          edge.target.startsWith(":feature:") &&
          targetLayer == "data"
      ) {
        violations +=
          "ui must not depend on concrete feature data: ${edge.source} --${edge.configuration}--> ${edge.target}"
      }
    }

    val adjacency =
      edges
        .groupBy(ProjectDependencyEdge::source)
        .mapValues { (_, projectEdges) -> projectEdges.map(ProjectDependencyEdge::target).distinct() }

    val visitState = mutableMapOf<String, Int>()
    val stack = mutableListOf<String>()
    val reportedCycles = mutableSetOf<String>()

    fun visit(projectPath: String) {
      visitState[projectPath] = 1
      stack += projectPath

      adjacency[projectPath].orEmpty().forEach { target ->
        when (visitState[target]) {
          1 -> {
            val cycleStart = stack.indexOf(target)
            if (cycleStart >= 0) {
              val cycle = (stack.subList(cycleStart, stack.size) + target).joinToString(" -> ")
              if (reportedCycles.add(cycle)) {
                violations += "Gradle project dependency cycle: $cycle"
              }
            }
          }

          2 -> Unit
          else -> visit(target)
        }
      }

      stack.removeAt(stack.lastIndex)
      visitState[projectPath] = 2
    }

    subprojects.map { it.path }.sorted().forEach { projectPath ->
      if (visitState[projectPath] == null) {
        visit(projectPath)
      }
    }

    val packagePattern = Regex("""(?m)^\s*package\s+([A-Za-z_][A-Za-z0-9_.]*)\s*$""")
    val androidImportPattern = Regex("""(?m)^\s*import\s+android\.""")
    subprojects.forEach { project ->
      listOf("src/main/java", "src/main/kotlin").forEach { sourceRootPath ->
        val sourceRoot = project.file(sourceRootPath)
        if (sourceRoot.isDirectory) {
          project.fileTree(sourceRoot) {
            include("**/*.kt")
          }.files.sortedBy { it.path }.forEach { sourceFile ->
            val sourceText = sourceFile.readText()
            val repositoryPath = sourceFile.relativeTo(rootDir).path.replace('\\', '/')

            violations += sourceArchitectureViolations(
              projectPath = project.path,
              repositoryPath = repositoryPath,
              sourceText = sourceText,
            )

            if (projectLayer(project.path) == "domain" && androidImportPattern.containsMatchIn(sourceText)) {
              violations +=
                "domain must not import Android framework types: ${project.path}:${sourceFile.relativeTo(project.projectDir)}"
            }

            val declaredPackage = packagePattern
              .find(sourceText)
              ?.groupValues
              ?.get(1)
              ?: return@forEach
            val expectedParentPath = declaredPackage.replace('.', '/')
            val actualParentPath = sourceFile.parentFile
              .relativeTo(sourceRoot)
              .path
              .replace('\\', '/')
            if (actualParentPath != expectedParentPath) {
              violations +=
                "Kotlin package/source path mismatch: ${project.path}:${sourceFile.relativeTo(project.projectDir)} " +
                  "declares $declaredPackage (expected parent $expectedParentPath)"
            }
          }
        }
      }
    }

    if (violations.isNotEmpty()) {
      throw GradleException(
        buildString {
          appendLine("Architecture verification failed (${violations.size} violation(s)):")
          violations.sorted().forEach { violation -> appendLine("- $violation") }
          append("See docs/adr/0003-multi-module-architecture.md and docs/adr/0046-automated-architecture-verification.md.")
        },
      )
    }

    logger.lifecycle("Architecture verification passed for ${edges.size} project dependency edge(s).")
  }
}

subprojects {
  pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
    dependencies.add("testImplementation", "junit:junit:4.13.2")
  }
  pluginManager.withPlugin("com.android.library") {
    dependencies.add("testImplementation", "junit:junit:4.13.2")
  }
  pluginManager.withPlugin("com.android.application") {
    dependencies.add("testImplementation", "junit:junit:4.13.2")

    extensions.configure<ApplicationExtension> {
      defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
      }
      testOptions {
        animationsDisabled = true
        managedDevices {
          localDevices {
            create("pixel6Api35") {
              device = "Pixel 6"
              apiLevel = 35
              systemImageSource = "google"
              require64Bit = true
              testedAbi = "arm64-v8a"
            }
          }
        }
      }
    }

    dependencies.add("androidTestImplementation", dependencies.platform("androidx.compose:compose-bom:2026.06.00"))
    dependencies.add("androidTestImplementation", "androidx.compose.ui:ui-test-junit4")
    dependencies.add("androidTestImplementation", "androidx.test:core-ktx:1.7.0")
    dependencies.add("androidTestImplementation", "androidx.test:runner:1.7.0")
    dependencies.add("androidTestImplementation", "androidx.test.ext:junit:1.3.0")
    dependencies.add("androidTestImplementation", "androidx.test.uiautomator:uiautomator:2.4.0")
  }
}
