package dev.terashima.yomitorirss.core.airuntime

import org.junit.Assert.assertEquals
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
}
