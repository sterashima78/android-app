package dev.terashima.yomitorirss.feature.game

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuTest {
  @Test
  fun `生成した問題の固定値と解が整合する`() {
    repeat(20) { seed ->
      val puzzle = SudokuPuzzleFactory(Random(seed)).create()

      assertEquals(81, puzzle.cells.size)
      assertEquals(81, puzzle.solution.size)
      assertTrue(isValidSudokuSolution(puzzle.solution))
      assertTrue(puzzle.cells.indices.all { puzzle.cells[it] == 0 || puzzle.cells[it] == puzzle.solution[it] })
    }
  }

  @Test
  fun `新しい問題はセル未選択で開始する`() {
    val puzzle = SudokuPuzzleFactory(Random(0)).create()

    assertNull(puzzle.newGameState().selectedIndex)
  }

  @Test
  fun `誤った数字は盤面へ入れずミスを加算する`() {
    val puzzle = SudokuPuzzleFactory(Random(1)).create()
    val index = puzzle.cells.indexOfFirst { it == 0 }
    val initial = puzzle.newGameState().selectCell(index)
    val wrong = (1..9).first { it != puzzle.solution[index] }

    val next = initial.enterNumber(wrong)

    assertEquals(0, next.entries[index])
    assertEquals(1, next.mistakes)
    assertEquals(index, next.selectedIndex)
  }

  @Test
  fun `正しい数字を入力すると選択を解除する`() {
    val puzzle = SudokuPuzzleFactory(Random(2)).create()
    val index = puzzle.cells.indexOfFirst { it == 0 }
    val initial = puzzle.newGameState().selectCell(index)

    val next = initial.enterNumber(puzzle.solution[index])

    assertEquals(puzzle.solution[index], next.entries[index])
    assertNull(next.selectedIndex)
  }

  @Test
  fun `固定値は変更できない`() {
    val puzzle = SudokuPuzzleFactory(Random(3)).create()
    val givenIndex = puzzle.cells.indexOfFirst { it != 0 }
    val initial = puzzle.newGameState().selectCell(givenIndex)

    val next = initial.enterNumber((puzzle.solution[givenIndex] % 9) + 1).clearSelectedCell()

    assertEquals(puzzle.cells[givenIndex], next.entries[givenIndex])
  }

  @Test
  fun `すべての空欄を正しく埋めると完成する`() {
    val puzzle = SudokuPuzzleFactory(Random(4)).create()
    var state = puzzle.newGameState()

    puzzle.cells.indices.filter { !puzzle.isGiven(it) }.forEach { index ->
      state = state.selectCell(index).enterNumber(puzzle.solution[index])
    }

    assertTrue(state.isCompleted)
    assertEquals(puzzle.solution, state.entries)
  }

  @Test
  fun `入力済みの可変セルは消去できる`() {
    val puzzle = SudokuPuzzleFactory(Random(5)).create()
    val index = puzzle.cells.indexOfFirst { it == 0 }
    val entered = puzzle.newGameState()
      .selectCell(index)
      .enterNumber(puzzle.solution[index])
      .selectCell(index)

    val cleared = entered.clearSelectedCell()

    assertFalse(cleared.isCompleted)
    assertEquals(0, cleared.entries[index])
  }
}
