package dev.terashima.yomitorirss.feature.settings.data

import dev.terashima.yomitorirss.feature.settings.AiModelRepository
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultAiModelRepositoryContractTest {
  @Test
  fun `実装はAiModelRepository契約を満たす`() {
    assertTrue(AiModelRepository::class.java.isAssignableFrom(DefaultAiModelRepository::class.java))
  }
}
