package dev.terashima.yomitorirss

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCompositionSourceArchitectureTest {
  @Test
  fun `app feature namespaceにはapp shell navigation以外のproduction sourceを置かない`() {
    val repositoryRoot = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
      .firstOrNull { File(it, "settings.gradle.kts").isFile && File(it, "app").isDirectory }
      ?: error("repository root not found")
    val featureRoot = File(
      repositoryRoot,
      "app/src/main/java/dev/terashima/yomitorirss/feature",
    )
    val unexpected = featureRoot
      .walkTopDown()
      .filter { it.isFile && it.extension == "kt" }
      .map { it.relativeTo(featureRoot).invariantSeparatorsPath }
      .filterNot { it.startsWith("navigation/") }
      .toList()

    assertTrue(
      "feature-owned UI or app composition adapters must not return to app/feature: $unexpected",
      unexpected.isEmpty(),
    )
  }
}
