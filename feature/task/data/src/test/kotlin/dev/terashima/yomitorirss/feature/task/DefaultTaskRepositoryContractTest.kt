package dev.terashima.yomitorirss.feature.task.data

import dev.terashima.yomitorirss.feature.task.TaskRepository
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultTaskRepositoryContractTest {
  @Test
  fun `実装はTaskRepository契約を満たす`() {
    assertTrue(TaskRepository::class.java.isAssignableFrom(DefaultTaskRepository::class.java))
  }
}
