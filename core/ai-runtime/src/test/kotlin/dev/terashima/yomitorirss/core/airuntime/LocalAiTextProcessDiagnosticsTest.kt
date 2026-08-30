package dev.terashima.yomitorirss.core.airuntime

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAiTextProcessDiagnosticsTest {
  @Test
  fun `process state summary は128bytes以内の安全なruntime情報だけを含む`() {
    val summary = buildLocalAiTextProcessStateSummary(
      LocalAiTextProcessDiagnosticState(
        mode = LocalAiTextProcessMode.STRUCTURED,
        phase = LocalAiTextProcessPhase.GENERATING_RESPONSE,
        backend = null,
        contextTokens = 8_192,
        speculativeDecodingEnabled = true,
      ),
    )

    assertEquals("ai=structured;phase=generate;ctx=8192;spec=1", summary)
    assertTrue(
      summary.toByteArray(StandardCharsets.US_ASCII).size <=
        LocalAiTextProcessDiagnostics.PROCESS_STATE_SUMMARY_MAX_BYTES,
    )
    assertFalse(summary.contains("prompt", ignoreCase = true))
    assertFalse(summary.contains("output", ignoreCase = true))
    assertFalse(summary.contains("modelId", ignoreCase = true))
  }

  @Test
  fun `subprocess memory report は同じpidの終了前10分だけを時系列で返す`() {
    val report = """
      timestamp=100 pid=10 process=dev.terashima.yomitorirss:local_ai_text mode=text phase=generate pssKb=10 rssKb=20 nativeHeapKb=30 javaHeapKb=40
      timestamp=980 pid=11 process=dev.terashima.yomitorirss:local_ai_text mode=text phase=generate pssKb=11 rssKb=21 nativeHeapKb=31 javaHeapKb=41
      timestamp=950 pid=10 process=dev.terashima.yomitorirss:local_ai_text mode=text phase=prepare pssKb=12 rssKb=22 nativeHeapKb=32 javaHeapKb=42
      timestamp=990 pid=10 process=dev.terashima.yomitorirss:local_ai_text mode=text phase=generate pssKb=13 rssKb=23 nativeHeapKb=33 javaHeapKb=43
      timestamp=1100 pid=10 process=dev.terashima.yomitorirss:local_ai_text mode=text phase=complete pssKb=14 rssKb=24 nativeHeapKb=34 javaHeapKb=44
    """.trimIndent()

    val filtered = filterLocalAiTextProcessDiagnosticLines(
      report = report,
      pid = 10,
      untilTimestamp = 1_000,
      windowMillis = 100,
    )

    assertNotNull(filtered)
    val lines = requireNotNull(filtered).lines()
    assertEquals(2, lines.size)
    assertTrue(lines[0].startsWith("timestamp=950"))
    assertTrue(lines[1].startsWith("timestamp=990"))
  }

  @Test
  fun `対象pidのsampleがなければsubprocess reportを返さない`() {
    val filtered = filterLocalAiTextProcessDiagnosticLines(
      report = "timestamp=990 pid=20 mode=text phase=generate",
      pid = 10,
      untilTimestamp = 1_000,
      windowMillis = 100,
    )

    assertNull(filtered)
  }
}
