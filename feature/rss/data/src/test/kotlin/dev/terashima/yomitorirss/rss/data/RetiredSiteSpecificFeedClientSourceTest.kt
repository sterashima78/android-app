package dev.terashima.yomitorirss.rss.data

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class RetiredSiteSpecificFeedClientSourceTest {
  private val repositoryRoot: File by lazy {
    generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
      .firstOrNull { File(it, "settings.gradle.kts").isFile && File(it, "feature").isDirectory }
      ?: error("repository root not found")
  }

  @Test
  fun `RSS dataは退役済みsite specific synthetic feed clientを持たない`() {
    val rssProductionRoot = File(repositoryRoot, "feature/rss/data/src/main")
    val forbiddenNames = setOf("MangaOneFeedClient.kt", "YanmagaFeedClient.kt")
    val unexpectedFiles = rssProductionRoot
      .walkTopDown()
      .filter(File::isFile)
      .filter { it.name in forbiddenNames }
      .map { it.relativeTo(repositoryRoot).invariantSeparatorsPath }
      .toList()

    assertTrue(
      "retired site-specific RSS clients must not return: $unexpectedFiles",
      unexpectedFiles.isEmpty(),
    )

    val implementationReferences = rssProductionRoot
      .walkTopDown()
      .filter(File::isFile)
      .filter { it.extension == "kt" }
      .filter { file ->
        val source = file.readText()
        "MangaOneFeedClient" in source || "YanmagaFeedClient" in source
      }
      .map { it.relativeTo(repositoryRoot).invariantSeparatorsPath }
      .toList()

    assertTrue(
      "RSS production source must use custom rules or standard feed acquisition instead of retired clients: $implementationReferences",
      implementationReferences.isEmpty(),
    )
  }
}
