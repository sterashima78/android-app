package dev.terashima.yomitorirss.feature.task

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskUiStateTest {
  @Test
  fun `初期状態は未完了フィルタで空になる`() {
    val state = TaskUiState()

    assertFalse(state.initialized)
    assertTrue(state.tasks.isEmpty())
    assertEquals(TaskFilter.UNFINISHED, state.filter)
    assertTrue(state.expandedIds.isEmpty())
    assertNull(state.error)
  }
}
