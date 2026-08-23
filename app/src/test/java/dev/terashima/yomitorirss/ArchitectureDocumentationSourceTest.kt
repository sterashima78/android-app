package dev.terashima.yomitorirss

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchitectureDocumentationSourceTest {
  private val repositoryRoot: File by lazy {
    generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
      .firstOrNull { File(it, "settings.gradle.kts").isFile && File(it, "docs").isDirectory }
      ?: error("repository root not found")
  }

  @Test
  fun `current context mapの旧互換入口を参照しない`() {
    val retiredPath = "docs/domain-context-map.md"
    assertFalse(
      "retired Context Map compatibility entry must stay removed",
      File(repositoryRoot, retiredPath).exists(),
    )

    val staleReferences = File(repositoryRoot, "docs")
      .walkTopDown()
      .filter { it.isFile && it.extension == "md" }
      .filter { retiredPath in it.readText() }
      .map { it.relativeTo(repositoryRoot).invariantSeparatorsPath }
      .toList()

    assertTrue(
      "architecture docs must link directly to docs/architecture/context-map.md: $staleReferences",
      staleReferences.isEmpty(),
    )
  }
}
