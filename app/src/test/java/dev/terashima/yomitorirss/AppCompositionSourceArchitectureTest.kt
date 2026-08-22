package dev.terashima.yomitorirss

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCompositionSourceArchitectureTest {
  private val repositoryRoot: File by lazy {
    generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
      .firstOrNull { File(it, "settings.gradle.kts").isFile && File(it, "app").isDirectory }
      ?: error("repository root not found")
  }

  @Test
  fun `app feature namespaceにはapp shell navigation以外のproduction sourceを置かない`() {
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

  @Test
  fun `AppContainerはfeature data implementationの直接構築を持たない`() {
    val source = File(
      repositoryRoot,
      "app/src/main/java/dev/terashima/yomitorirss/AppContainer.kt",
    ).readText()

    assertFalse(
      "AppContainer should delegate feature data construction to runtime dependency groups",
      Regex("(?m)^import dev\\.terashima\\.yomitorirss\\.feature\\..+\\.data\\.").containsMatchIn(source),
    )
  }

  @Test
  fun `Integrated projectionはComposeとAndroid frameworkに依存しない`() {
    val source = File(
      repositoryRoot,
      "app/src/main/java/dev/terashima/yomitorirss/ui/IntegratedProjection.kt",
    ).readText()

    assertFalse(
      "Integrated projection should remain a pure cross-feature mapper",
      Regex("(?m)^import (?:android\\.|androidx\\.compose\\.)").containsMatchIn(source),
    )
  }
}
