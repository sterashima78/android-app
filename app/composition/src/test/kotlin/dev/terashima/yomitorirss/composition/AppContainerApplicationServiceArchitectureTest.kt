package dev.terashima.yomitorirss.composition

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class AppContainerApplicationServiceArchitectureTest {
  @Test
  fun `AppContainerはBookmark enrichment policyを実行しない`() {
    val source = repositoryFile(
      "app/composition/src/main/java/dev/terashima/yomitorirss/AppContainer.kt",
    ).readText()

    assertFalse(source.contains("shouldRequestBookmarkEnrichment("))
    assertFalse(source.contains("requestBookmarkEnrichment(articleId)"))
  }

  private fun repositoryFile(path: String): File {
    val start = File(System.getProperty("user.dir")).absoluteFile
    return generateSequence(start) { current -> current.parentFile }
      .map { root -> File(root, path) }
      .firstOrNull(File::isFile)
      ?: error("Repository source file not found: $path (start=$start)")
  }
}
