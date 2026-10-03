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
  fun `Local model artifactは検証と昇格後にだけcurrent revisionとして有効化する`() {
    val source = File(
      repositoryRoot,
      "core/ai-runtime/src/main/kotlin/dev/terashima/yomitorirss/core/airuntime/LocalModelManager.kt",
    ).readText()
    val download = source
      .substringAfter("fun downloadModel(modelId: String) {")
      .substringBefore("fun countTokens")
    val select = source
      .substringAfter("fun selectModel(modelId: String) {")
      .substringBefore("fun deleteModel")
    val delete = source
      .substringAfter("fun deleteModel(modelId: String) {")
      .substringBefore("fun cancel()")
    val cleanup = source
      .substringAfter("private fun cleanupOutdatedModelArtifacts() {")
      .substringBefore("private fun isExpectedModelArtifact")
    val validation = download.indexOf("check(isExpectedModelArtifact(temporary, model))")
    val promotion = download.indexOf("if (!temporary.renameTo(destination))")
    val revisionMarker = download.indexOf(
      "preferences.edit().putString(modelRevisionKey(model), model.artifactRevision).apply()",
    )

    assertTrue("temporary artifact must be validated before promotion", validation >= 0 && validation < promotion)
    assertTrue("current revision marker must be written only after promotion", promotion < revisionMarker)
    assertTrue(
      "download failure must remove the temporary artifact",
      "} catch (error: Throwable) {" in download &&
        "temporary.delete()" in download.substringAfter("} catch (error: Throwable) {"),
    )
    assertTrue(
      "model selection must require a valid artifact",
      "check(isValidModelFile(modelFile(model), model))" in select,
    )
    assertTrue("model deletion must remove the installed artifact", "modelFile(model).delete()" in delete)
    assertTrue("model deletion must remove the temporary artifact", "temporaryModelFile(model).delete()" in delete)
    assertTrue("model deletion must remove derived caches", "modelCacheDirectory(model).deleteRecursively()" in delete)
    assertTrue("model deletion must remove the revision marker", "remove(modelRevisionKey(model))" in delete)
    assertTrue("selected model deletion must clear selection", "remove(SELECTED_MODEL_KEY)" in delete)
    assertTrue("outdated artifacts must be removed at startup", "file.delete()" in cleanup)
    assertTrue("outdated temporary artifacts must be removed at startup", "temporaryModelFile(model).delete()" in cleanup)
    assertTrue("outdated caches must be removed at startup", "modelCacheDirectory(model).deleteRecursively()" in cleanup)
    assertTrue("outdated revision markers must be removed at startup", "remove(modelRevisionKey(model))" in cleanup)
    assertTrue(
      "validity must require both artifact validation and current revision",
      "isExpectedModelArtifact(file, model) &&" in source &&
        "preferences.getString(modelRevisionKey(model), null) == model.artifactRevision" in source,
    )
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
}
