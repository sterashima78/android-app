package dev.terashima.yomitorirss.core.airuntime

import org.junit.Assert.assertEquals
import org.junit.Test

class ProcessIsolatedLocalAiStructuredTextInferenceTest {
  @Test
  fun `structured inference はsubprocess deathを1回だけ再試行する`() {
    assertEquals(2, STRUCTURED_TEXT_PROCESS_MAX_ATTEMPTS)
  }
}
