package dev.terashima.yomitorirss.feature.summary.data

import dev.terashima.yomitorirss.feature.summary.SummaryExecutionProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryProviderTransitionTest {
  @Test
  fun `provider切替は旧work停止_再queue_新provider再scheduleの順で行う`() {
    val events = mutableListOf<String>()

    restartSummaryProviderPipeline(
      cancelInference = { events += "cancel-inference" },
      cancelContentFetch = { events += "cancel-content-fetch" },
      requeueInterrupted = { events += "requeue" },
      scheduleSelectedProvider = { events += "schedule" },
    )

    assertEquals(
      listOf("cancel-inference", "cancel-content-fetch", "requeue", "schedule"),
      events,
    )
  }

  @Test
  fun `local providerはlocal pauseだけを尊重する`() {
    assertTrue(
      isSummaryProviderPaused(
        SummaryExecutionProvider.LOCAL,
        localPaused = true,
        cloudPaused = false,
      ),
    )
    assertFalse(
      isSummaryProviderPaused(
        SummaryExecutionProvider.LOCAL,
        localPaused = false,
        cloudPaused = true,
      ),
    )
  }

  @Test
  fun `chatgpt providerはcloud pauseだけを尊重する`() {
    assertTrue(
      isSummaryProviderPaused(
        SummaryExecutionProvider.CHATGPT,
        localPaused = false,
        cloudPaused = true,
      ),
    )
    assertFalse(
      isSummaryProviderPaused(
        SummaryExecutionProvider.CHATGPT,
        localPaused = true,
        cloudPaused = false,
      ),
    )
  }
}
