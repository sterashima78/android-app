package dev.terashima.yomitorirss.feature.summary.data

import dev.terashima.yomitorirss.feature.summary.SummaryTaskQueueRepository
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultSummaryTaskQueueRepositoryContractTest {
  @Test
  fun `実装はSummaryTaskQueueRepository契約を満たす`() {
    assertTrue(
      SummaryTaskQueueRepository::class.java.isAssignableFrom(DefaultSummaryTaskQueueRepository::class.java),
    )
  }
}
