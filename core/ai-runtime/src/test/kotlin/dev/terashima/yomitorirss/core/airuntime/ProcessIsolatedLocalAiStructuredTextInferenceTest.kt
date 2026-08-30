package dev.terashima.yomitorirss.core.airuntime

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessIsolatedLocalAiStructuredTextInferenceTest {
  private val repositoryRoot: File by lazy {
    generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
      .firstOrNull { File(it, "settings.gradle.kts").isFile && File(it, "core").isDirectory }
      ?: error("repository root not found")
  }

  @Test
  fun `structured inference はsubprocess deathを1回だけ再試行する`() {
    assertEquals(2, STRUCTURED_TEXT_PROCESS_MAX_ATTEMPTS)
  }

  @Test
  fun `structured inference はimmutable snapshotを確定してからRemoteExceptionだけを再試行する`() {
    val source = File(
      repositoryRoot,
      "core/ai-runtime/src/main/kotlin/dev/terashima/yomitorirss/core/airuntime/ProcessIsolatedLocalAiStructuredTextInference.kt",
    ).readText()

    val snapshotIndex = source.indexOf("val snapshot = captureStructuredTextSnapshot(appContext, manager)")
    val retryIndex = source.indexOf("repeat(STRUCTURED_TEXT_PROCESS_MAX_ATTEMPTS)")

    assertTrue(snapshotIndex >= 0)
    assertTrue(retryIndex > snapshotIndex)
    assertTrue(source.contains("catch (error: RemoteException)"))
    assertTrue(source.contains("if (attempt == STRUCTURED_TEXT_PROCESS_MAX_ATTEMPTS - 1) throw error"))
    assertFalse(source.contains("catch (error: IllegalStateException)"))
  }
}
