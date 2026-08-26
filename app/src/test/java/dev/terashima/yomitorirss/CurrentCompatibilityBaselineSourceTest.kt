package dev.terashima.yomitorirss

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentCompatibilityBaselineSourceTest {
  private val repositoryRoot: File by lazy {
    generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
      .firstOrNull { File(it, "settings.gradle.kts").isFile && File(it, "app").isDirectory }
      ?: error("repository root not found")
  }

  @Test
  fun `LocalModelManagerは退役済みrevision marker migrationを持たない`() {
    val source = File(
      repositoryRoot,
      "core/ai-runtime/src/main/kotlin/dev/terashima/yomitorirss/core/airuntime/LocalModelManager.kt",
    ).readText()

    assertFalse(source.contains("migrateLegacyCurrentModelRevisionMarkers"))
    assertTrue(
      source.contains(
        "preferences.getString(modelRevisionKey(model), null) == model.artifactRevision",
      ),
    )
  }

  @Test
  fun `production sourceは退役済みSummary実行設定を参照しない`() {
    val unexpected = productionKotlinFiles()
      .filter { file ->
        val source = file.readText()
        "summary_queue_execution" in source || "migrated_from_summary_queue_execution" in source
      }
      .map { it.relativeTo(repositoryRoot).invariantSeparatorsPath }
      .toList()

    assertTrue(
      "retired Summary preference compatibility must not return to production source: $unexpected",
      unexpected.isEmpty(),
    )
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

  private fun productionKotlinFiles(): Sequence<File> = repositoryRoot.walkTopDown()
    .filter(File::isFile)
    .filter { it.extension == "kt" }
    .filter { "/src/main/" in it.invariantSeparatorsPath }
}
