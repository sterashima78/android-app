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
      "Provider lookup must be limited to audited Android/WorkManager entry points. " +
        "Update code instead of extending the manifest for normal composition paths.",
      expected,
      actual,
    )
  }

  @Test
  fun `production code は YomitoriApplication への直接castを行わない`() {
    val root = repositoryRoot()
    val violations = productionKotlinFiles(root).flatMap { file ->
      val relativePath = file.relativeTo(root).path.replace('\\', '/')
      APPLICATION_CAST.findAll(file.readText()).map { "$relativePath:${it.value}" }.toList()
    }

    assertTrue(
      "Use an audited framework provider contract instead of YomitoriApplication service locator: $violations",
      violations.isEmpty(),
    )
  }

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
private const val PROVIDER_MANIFEST = "config/architecture/framework-provider-lookups.tsv"
