package dev.terashima.yomitorirss.feature.library.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmbCoverPrefetchProcessorTest {
  @Test
  fun `PDF表紙先読みは128MB以下だけを対象にする`() {
    val limit = 128L * 1024 * 1024

    assertFalse(shouldPrefetchPdf(-1L))
    assertTrue(shouldPrefetchPdf(0L))
    assertTrue(shouldPrefetchPdf(limit))
    assertFalse(shouldPrefetchPdf(limit + 1L))
  }
}
