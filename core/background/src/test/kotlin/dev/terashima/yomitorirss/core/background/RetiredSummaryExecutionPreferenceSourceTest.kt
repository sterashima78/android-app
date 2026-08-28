package dev.terashima.yomitorirss.core.background

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class RetiredSummaryExecutionPreferenceSourceTest {
  private val repositoryRoot: File by lazy {
    generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
      .firstOrNull { File(it, "settings.gradle.kts").isFile && File(it, "core").isDirectory }
      ?: error("repository root not found")
  }

  @Test
  fun `production sourceは退役済みSummary実行設定を参照しない`() {
    val unexpected = repositoryRoot.walkTopDown()
      .filter(File::isFile)
      .filter { it.extension == "kt" }
      .filter { "/src/main/" in it.invariantSeparatorsPath }
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
}
