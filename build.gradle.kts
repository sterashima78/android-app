import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.GradleException
import org.gradle.api.artifacts.ProjectDependency

plugins {
  id("com.android.application") version "9.3.0" apply false
  id("com.android.library") version "9.3.0" apply false
  id("org.jetbrains.kotlin.jvm") version "2.3.21" apply false
  id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
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

fun isArchitectureDependencyConfiguration(name: String): Boolean {
  if ("test" in name.lowercase()) return false

  return name in baseArchitectureDependencyConfigurations ||
    variantArchitectureDependencyConfigurationSuffixes.any { suffix -> name.endsWith(suffix) }
}

fun projectLayer(path: String): String = path.substringAfterLast(':')

val verifyArchitecture by tasks.registering {
  group = "verification"
  description = "Verifies Gradle dependency rules and production source layout defined by the architecture ADRs."

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
    subprojects.forEach { project ->
      listOf("src/main/java", "src/main/kotlin").forEach { sourceRootPath ->
        val sourceRoot = project.file(sourceRootPath)
        if (sourceRoot.isDirectory) {
          project.fileTree(sourceRoot) {
            include("**/*.kt")
          }.files.sortedBy { it.path }.forEach { sourceFile ->
            val declaredPackage = packagePattern
              .find(sourceFile.readText())
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