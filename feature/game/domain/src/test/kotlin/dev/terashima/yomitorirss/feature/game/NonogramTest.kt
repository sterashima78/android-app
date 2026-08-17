package dev.terashima.yomitorirss.feature.game

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NonogramTest {
  @Test
  fun `連続した塗りマスからヒントを生成する`() {
    assertEquals(listOf(2, 1), nonogramClues(listOf(true, true, false, true, false)))
    assertEquals(listOf(0), nonogramClues(listOf(false, false, false, false, false)))
  }

  @Test
  fun `生成した問題は5x5で行列ヒントを持つ`() {
    repeat(20) { seed ->
      val puzzle = NonogramPuzzleFactory(Random(seed)).create()
      assertEquals(5, puzzle.width)
      assertEquals(5, puzzle.height)
      assertEquals(5, puzzle.rowClues.size)
      assertEquals(5, puzzle.columnClues.size)
      assertTrue(puzzle.solution.any { it })
    }
  }

  @Test
  fun `解の塗りマスをすべて入力すると完成する`() {
    val puzzle = NonogramPuzzle(2, 2, listOf(true, false, false, true))
    val state = puzzle.newGameState().fill(0).fill(3)
    assertTrue(state.isCompleted)
  }

  @Test
  fun `余分な塗りマスがある間は完成しない`() {
    val puzzle = NonogramPuzzle(2, 2, listOf(true, false, false, true))
    val state = puzzle.newGameState().fill(0).fill(1).fill(3)
    assertFalse(state.isCompleted)
  }

  @Test
  fun `印は同じマスでもう一度押すと解除する`() {
    val state = NonogramPuzzle(1, 1, listOf(false)).newGameState()
    assertEquals(NonogramCellState.UNKNOWN, state.mark(0).mark(0).cells[0])
  }
}
