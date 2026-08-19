package dev.terashima.yomitorirss

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityArchitectureTest {
  @Test
  fun `MainActivity はfeature固有ViewModelを直接所有しない`() {
    val source = repositoryFile(
      "app/src/main/java/dev/terashima/yomitorirss/MainActivity.kt",
    ).readText()
    val featureViewModelImports = Regex(
      """(?m)^\s*import\s+dev\.terashima\.yomitorirss\.feature\.(?!navigation\.AppViewModel\s*$)[A-Za-z0-9_.]+ViewModel\s*$""",
    ).findAll(source).map { it.value.trim() }.toList()

    assertTrue(
      "MainActivity must not import feature-owned ViewModels: $featureViewModelImports",
      featureViewModelImports.isEmpty(),
    )
  }

  private fun repositoryFile(path: String): File {
    val start = File(System.getProperty("user.dir")).absoluteFile
    return generateSequence(start) { current -> current.parentFile }
      .map { root -> File(root, path) }
      .firstOrNull(File::isFile)
      ?: error("Repository source file not found: $path (start=$start)")
  }
}
