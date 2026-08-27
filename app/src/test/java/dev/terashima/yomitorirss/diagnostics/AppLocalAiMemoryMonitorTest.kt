package dev.terashima.yomitorirss.diagnostics

import dev.terashima.yomitorirss.core.airuntime.LocalAiProcessMemoryPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLocalAiMemoryMonitorTest {
  @Test
  fun `AI実行中はactiveとして採取する`() {
    val window = LocalAiMemorySamplingWindow(retainedWindowMillis = 1_000L)

    val request = window.next(
      nowMillis = 100L,
      activeDiagnosticLabel = "dev.example.SummaryWorker",
    )

    assertEquals(LocalAiProcessMemoryPhase.ACTIVE_BACKGROUND_AI, request?.phase)
    assertEquals("dev.example.SummaryWorker", request?.diagnosticLabel)
  }

  @Test
  fun `AI終了後は保持期間だけ直前のタスク名で採取する`() {
    val window = LocalAiMemorySamplingWindow(retainedWindowMillis = 1_000L)
    window.next(nowMillis = 100L, activeDiagnosticLabel = "dev.example.KnowledgeWorker")

    val retained = window.next(nowMillis = 1_000L, activeDiagnosticLabel = null)
    val expired = window.next(nowMillis = 1_101L, activeDiagnosticLabel = null)

    assertEquals(LocalAiProcessMemoryPhase.RETAINED_AFTER_BACKGROUND_AI, retained?.phase)
    assertEquals("dev.example.KnowledgeWorker", retained?.diagnosticLabel)
    assertNull(expired)
  }

  @Test
  fun `AI未実行なら採取しない`() {
    val window = LocalAiMemorySamplingWindow(retainedWindowMillis = 1_000L)

    assertNull(window.next(nowMillis = 100L, activeDiagnosticLabel = null))
  }
}
