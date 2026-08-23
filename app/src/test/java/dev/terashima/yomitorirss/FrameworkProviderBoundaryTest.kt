package dev.terashima.yomitorirss

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameworkProviderBoundaryTest {
  @Test
  fun `framework entry point の provider lookup は監査済み一覧に限定する`() {
    val root = repositoryRoot()
    val expected = File(root, PROVIDER_MANIFEST)
      .readLines()
      .filter { line -> line.isNotBlank() && !line.startsWith("#") }
      .map { line ->
        val columns = line.split('\t')
        require(columns.size == 3 && columns[2].isNotBlank()) {
          "Invalid framework provider manifest row: $line"
        }
        ProviderLookup(columns[0], columns[1])
      }
      .toSet()

    val actual = productionKotlinFiles(root)
      .flatMap { file ->
        val relativePath = file.relativeTo(root).path.replace('\\', '/')
        PROVIDER_CAST.findAll(file.readText()).map { match ->
          ProviderLookup(relativePath, match.groupValues[1])
        }.toList()
      }
      .toSet()

    assertEquals(
      "Provider lookup must be limited to audited Android framework entry points. " +
        "Update code instead of extending the manifest for normal composition paths.",
      expected,
      actual,
    )
  }

  @Test
  fun `WorkManager Worker は Application provider lookupを使わない`() {
    val root = repositoryRoot()
    val violations = workerFiles(root)
      .flatMap { file ->
        val relativePath = file.relativeTo(root).path.replace('\\', '/')
        PROVIDER_CAST.findAll(file.readText()).map { match ->
          "$relativePath:${match.value}"
        }.toList()
      }
      .toList()

    assertTrue(
      "WorkManager Workers must receive dependencies through WorkerFactory constructor injection: $violations",
      violations.isEmpty(),
    )
  }

  @Test
  fun `WorkManager Worker は feature data layer が所有する`() {
    val root = repositoryRoot()
    val violations = workerFiles(root)
      .map { file -> file.relativeTo(root).path.replace('\\', '/') }
      .filter { path ->
        path.startsWith("feature/") &&
          ("/ui/src/main/" in path || "/domain/src/main/" in path)
      }
      .toList()

    assertTrue(
      "Feature WorkManager Workers must live in the owning data layer: $violations",
      violations.isEmpty(),
    )
  }

  @Test
  fun `WorkManager Worker は application scope graph を再構築しない`() {
    val root = repositoryRoot()
    val violations = workerFiles(root).flatMap { file ->
      val relativePath = file.relativeTo(root).path.replace('\\', '/')
      val source = file.readText()
      WORKER_PARALLEL_GRAPH_PATTERN.findAll(source).map { match ->
        "$relativePath:${match.value}"
      }.toList()
    }.toList()

    assertTrue(
      "Workers must receive application-scope database/repository/scheduler dependencies from WorkerFactory: $violations",
      violations.isEmpty(),
    )
  }

  @Test
  fun `WorkManager は application WorkerFactory を on-demand 設定する`() {
    val root = repositoryRoot()
    val applicationSource = File(
      root,
      "app/src/main/java/dev/terashima/yomitorirss/YomitoriApplication.kt",
    ).readText()
    val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()

    assertTrue(
      "YomitoriApplication must provide WorkManager Configuration",
      "Configuration.Provider" in applicationSource,
    )
    assertTrue(
      "WorkManager configuration must install the application WorkerFactory",
      ".setWorkerFactory(" in applicationSource,
    )
    assertTrue(
      "Default WorkManager startup initializer must be removed for on-demand configuration",
      "androidx.work.WorkManagerInitializer" in manifest &&
        Regex(
          """(?s)android:name=\"androidx\.work\.WorkManagerInitializer\".*?tools:node=\"remove\"""",
        ).containsMatchIn(manifest),
    )
  }

  @Test
  fun `production code は YomitoriApplication への直接castを行わない`() {
    val root = repositoryRoot()
    val violations = productionKotlinFiles(root).flatMap { file ->
      val relativePath = file.relativeTo(root).path.replace('\\', '/')
      APPLICATION_CAST.findAll(file.readText()).map { "$relativePath:${it.value}" }.toList()
    }.toList()

    assertTrue(
      "Use an audited framework provider contract instead of YomitoriApplication service locator: $violations",
      violations.isEmpty(),
    )
  }

  private fun workerFiles(root: File): Sequence<File> = productionKotlinFiles(root)
    .filter { file -> WORKER_DECLARATION.containsMatchIn(file.readText()) }

  private fun productionKotlinFiles(root: File): Sequence<File> = root.walkTopDown()
    .filter(File::isFile)
    .filter { file -> file.extension == "kt" }
    .filter { file -> "/src/main/" in file.relativeTo(root).path.replace('\\', '/') }

  private fun repositoryRoot(): File {
    val start = File(System.getProperty("user.dir")).absoluteFile
    return generateSequence(start) { current -> current.parentFile }
      .firstOrNull { root -> File(root, "settings.gradle.kts").isFile }
      ?: error("Repository root not found from $start")
  }
}

private data class ProviderLookup(
  val path: String,
  val provider: String,
)

private val PROVIDER_CAST = Regex("""\bas\?\s*([A-Za-z_][A-Za-z0-9_]*Provider)\b""")
private val APPLICATION_CAST = Regex("""\bas\??\s*YomitoriApplication\b""")
private val WORKER_DECLARATION = Regex(
  """:\s*(?:androidx\.work\.)?(?:CoroutineWorker|Worker|ListenableWorker)\s*\(""",
)
private val WORKER_PARALLEL_GRAPH_PATTERN = Regex(
  """(?:YomitoriDatabase\.create\s*\(|DatabaseConnection\s*\(|Default[A-Za-z0-9_]*Repository\s*\(|WorkManager[A-Za-z0-9_]*(?:Scheduler|Controller)\s*\(|require[A-Za-z0-9_]*Repository\s*\()""",
)
private const val PROVIDER_MANIFEST = "config/architecture/framework-provider-lookups.tsv"
