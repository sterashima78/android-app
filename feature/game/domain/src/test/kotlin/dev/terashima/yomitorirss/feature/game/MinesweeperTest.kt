package dev.terashima.yomitorirss.feature.game

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MinesweeperTest {
  @Test
  fun `最初に開いたマスと周囲には地雷を置かない`() {
    val game = MinesweeperGame(Random(0))
    val first = 40
    val state = game.reveal(game.newGame(), first)
    assertTrue(state.initialized)
    assertEquals(10, state.cells.count { it.isMine })
    assertFalse(state.cells[first].isMine)
    assertEquals(0, state.cells[first].adjacentMines)
    assertTrue(neighbors(first, state.width, state.height).none { state.cells[it].isMine })
  }

  @Test
  fun `隣接地雷数は実際の地雷配置と一致する`() {
    val game = MinesweeperGame(Random(1))
    val state = game.reveal(game.newGame(), 0)
    state.cells.indices.filterNot { state.cells[it].isMine }.forEach { index ->
      val expected = neighbors(index, state.width, state.height).count { state.cells[it].isMine }
      assertEquals(expected, state.cells[index].adjacentMines)
    }
  }

  @Test
  fun `旗は残数を減らし再操作で解除できる`() {
    val game = MinesweeperGame(Random(2))
    val initial = game.newGame()
    val flagged = game.toggleFlag(initial, 1)
    val cleared = game.toggleFlag(flagged, 1)
    assertEquals(9, flagged.flagsRemaining)
    assertEquals(MinesweeperVisibility.FLAGGED, flagged.cells[1].visibility)
    assertEquals(10, cleared.flagsRemaining)
    assertEquals(MinesweeperVisibility.HIDDEN, cleared.cells[1].visibility)
  }

  @Test
  fun `地雷を開くと敗北して地雷を表示する`() {
    val game = MinesweeperGame(Random(3))
    val initialized = game.reveal(game.newGame(), 0)
    val mineIndex = initialized.cells.indexOfFirst { it.isMine }
    val lost = game.reveal(initialized, mineIndex)
    assertEquals(MinesweeperStatus.LOST, lost.status)
    assertTrue(lost.cells.filter { it.isMine }.all { it.visibility == MinesweeperVisibility.REVEALED })
  }

  @Test
  fun `地雷以外をすべて開くと勝利する`() {
    val game = MinesweeperGame(Random(4))
    var state = game.reveal(game.newGame(), 0)
    val safeIndices = state.cells.indices.filterNot { state.cells[it].isMine }
    safeIndices.forEach { index -> state = game.reveal(state, index) }
    assertEquals(MinesweeperStatus.WON, state.status)
  }
}
