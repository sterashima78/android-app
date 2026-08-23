package dev.terashima.yomitorirss.core.airuntime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAiMemoryDiagnosticsTest {
  @Test
  fun `診断行は指定件数を超えると古いものから捨てる`() {
    val updated = appendDiagnosticLine(
      existing = "first\nsecond\nthird",
      newLine = "fourth",
      maxLines = 3,
    )

    assertEquals("second\nthird\nfourth", updated)
  }

  @Test
  fun `空行は診断履歴へ残さない`() {
    val updated = appendDiagnosticLine(
      existing = "first\n\nsecond",
      newLine = "third",
      maxLines = 4,
    )

    assertEquals("first\nsecond\nthird", updated)
  }

  @Test
  fun `engine close 失敗は例外型だけを診断行へ残す`() {
    val line = buildVisionMemoryDiagnosticLine(
      timestamp = 123L,
      phase = LocalAiMemoryDiagnosticPhase.VISION_AFTER_ENGINE_RELEASE,
      pssKb = 10L,
      rssKb = 20L,
      nativeHeapKb = 30L,
      javaHeapKb = 40L,
      engineCloseStatus = LocalAiEngineCloseStatus.FAILED,
      engineCloseErrorClass = "java.lang.IllegalStateException",
    )

    assertTrue(line.contains("phase=vision-after-engine-release"))
    assertTrue(line.contains("engineClose=failed"))
    assertTrue(line.contains("engineCloseError=java.lang.IllegalStateException"))
    assertFalse(line.contains("message="))
  }

  @Test
  fun `engine close 情報がないフェーズには close field を追加しない`() {
    val line = buildVisionMemoryDiagnosticLine(
      timestamp = 123L,
      phase = LocalAiMemoryDiagnosticPhase.VISION_AFTER_ENGINE_INIT,
      pssKb = 10L,
      rssKb = null,
      nativeHeapKb = 30L,
      javaHeapKb = 40L,
    )

    assertTrue(line.contains("phase=vision-after-engine-init"))
    assertTrue(line.contains("rssKb=unknown"))
    assertFalse(line.contains("engineClose="))
    assertFalse(line.contains("engineCloseError="))
  }
}
