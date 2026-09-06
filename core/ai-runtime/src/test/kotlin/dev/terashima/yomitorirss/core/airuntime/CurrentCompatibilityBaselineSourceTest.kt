package dev.terashima.yomitorirss.core.airuntime

import java.io.File
import org.junit.Assert.assertEquals
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

  @Test
  fun `LocalModelManagerは退役済みQwen artifact cleanupを持たない`() {
    val source = File(
      repositoryRoot,
      "core/ai-runtime/src/main/kotlin/dev/terashima/yomitorirss/core/airuntime/LocalModelManager.kt",
    ).readText()

    assertFalse(source.contains("cleanupRetiredModelArtifacts"))
    assertFalse(source.contains("qwen2.5-0.5b-q8"))
    assertFalse(source.contains("qwen2.5-1.5b-q8"))
    assertFalse(source.contains("qwen3-4b-mixed-int4"))
    assertTrue(source.contains("cleanupOutdatedModelArtifacts"))
  }

  @Test
  fun `LocalAiMemoryDiagnosticsは統合済みreport keyだけを利用する`() {
    val source = File(
      repositoryRoot,
      "core/ai-runtime/src/main/kotlin/dev/terashima/yomitorirss/core/airuntime/LocalAiMemoryDiagnostics.kt",
    ).readText()

    assertFalse(source.contains("recent_vision_memory_samples"))
    assertTrue(source.contains("recent_inference_memory_samples"))
  }

  @Test
  fun `LiteRT LM更新時はJNI用R8 ruleを再確認する`() {
    val buildFile = File(repositoryRoot, "core/ai-runtime/build.gradle.kts").readText()
    val proguardRules = File(repositoryRoot, "app/proguard-rules.pro").readText()
    val version = Regex("litertlm-android:([0-9.]+)")
      .find(buildFile)
      ?.groupValues
      ?.get(1)

    assertEquals("0.16.1", version)
    assertTrue(proguardRules.contains("LiteRT-LM 0.16.1 JNI"))
    assertFalse(proguardRules.contains("-keep class com.google.ai.edge.litertlm.** { *; }"))
    assertTrue(proguardRules.contains("com.google.ai.edge.litertlm.InputData\$Text"))
    assertTrue(proguardRules.contains("com.google.ai.edge.litertlm.LiteRtLmJni\$JniMessageCallback"))
  }
}
