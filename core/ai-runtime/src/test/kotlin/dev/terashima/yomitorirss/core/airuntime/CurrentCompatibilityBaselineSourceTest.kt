package dev.terashima.yomitorirss.core.airuntime

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentCompatibilityBaselineSourceTest {
  private val repositoryRoot: File by lazy {
    generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
      .firstOrNull { File(it, "settings.gradle.kts").isFile && File(it, "core").isDirectory }
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
}
