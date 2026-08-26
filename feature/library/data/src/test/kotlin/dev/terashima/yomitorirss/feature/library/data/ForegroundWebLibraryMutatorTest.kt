package dev.terashima.yomitorirss.feature.library.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundWebLibraryMutatorTest {
  @Test
  fun `前面Activityが利用可能になるまで再試行する`() = runBlocking {
    var attempts = 0

    awaitWebLibraryForegroundAvailability(retryDelayMillis = 1L) {
      attempts += 1
      attempts >= 3
    }

    assertEquals(3, attempts)
  }
}
