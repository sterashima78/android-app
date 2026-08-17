package dev.terashima.yomitorirss.feature.game

import kotlin.random.Random

enum class MinesweeperVisibility { HIDDEN, REVEALED, FLAGGED }
enum class MinesweeperStatus { PLAYING, WON, LOST }

data class MinesweeperCell(
  val isMine: Boolean = false,
  val adjacentMines: Int = 0,
  val visibility: MinesweeperVisibility = MinesweeperVisibility.HIDDEN,
)

data class MinesweeperState(
  val width: Int = 9,
  val height: Int = 9,
  val mineCount: Int = 10,
  val cells: List<MinesweeperCell> = List(width * height) { MinesweeperCell() },
  val initialized: Boolean = false,
  val status: MinesweeperStatus = MinesweeperStatus.PLAYING,
) {
  init {
    require(width > 0 && height > 0)
    require(mineCount in 1 until width * height)
    require(cells.size == width * height)
  }

  val flagsRemaining: Int
    get() = mineCount - cells.count { it.visibility == MinesweeperVisibility.FLAGGED }
}

class MinesweeperGame(
  private val random: Random = Random.Default,
  private val width: Int = 9,
  private val height: Int = 9,
  private val mineCount: Int = 10,
) {
  fun newGame(): MinesweeperState = MinesweeperState(width, height, mineCount)

  fun toggleFlag(state: MinesweeperState, index: Int): MinesweeperState {
    if (state.status != MinesweeperStatus.PLAYING || index !in state.cells.indices) return state
    val current = state.cells[index]
    if (current.visibility == MinesweeperVisibility.REVEALED) return state
    if (current.visibility == MinesweeperVisibility.HIDDEN && state.flagsRemaining <= 0) return state
    val nextVisibility = if (current.visibility == MinesweeperVisibility.FLAGGED) {
      MinesweeperVisibility.HIDDEN
    } else {
      MinesweeperVisibility.FLAGGED
    }
    val next = state.cells.toMutableList().also { it[index] = current.copy(visibility = nextVisibility) }
    return state.copy(cells = next)
  }

  fun reveal(state: MinesweeperState, index: Int): MinesweeperState {
    if (state.status != MinesweeperStatus.PLAYING || index !in state.cells.indices) return state
    if (state.cells[index].visibility == MinesweeperVisibility.FLAGGED) return state

    var working = if (state.initialized) state else initialize(state, index)
    if (working.cells[index].isMine) {
      val revealed = working.cells.map { cell ->
        if (cell.isMine) cell.copy(visibility = MinesweeperVisibility.REVEALED) else cell
      }
      return working.copy(cells = revealed, status = MinesweeperStatus.LOST)
    }

    val nextCells = working.cells.toMutableList()
    val queue = ArrayDeque<Int>()
    queue.add(index)
    val visited = mutableSetOf<Int>()
    while (queue.isNotEmpty()) {
      val currentIndex = queue.removeFirst()
      if (!visited.add(currentIndex)) continue
      val cell = nextCells[currentIndex]
      if (cell.visibility == MinesweeperVisibility.FLAGGED || cell.isMine) continue
      nextCells[currentIndex] = cell.copy(visibility = MinesweeperVisibility.REVEALED)
      if (cell.adjacentMines == 0) {
        neighbors(currentIndex, working.width, working.height).forEach { neighbor ->
          if (neighbor !in visited && !nextCells[neighbor].isMine) queue.add(neighbor)
        }
      }
    }

    val won = nextCells.indices.all { cellIndex ->
      nextCells[cellIndex].isMine || nextCells[cellIndex].visibility == MinesweeperVisibility.REVEALED
    }
    working = working.copy(
      cells = nextCells,
      status = if (won) MinesweeperStatus.WON else MinesweeperStatus.PLAYING,
    )
    return working
  }

  private fun initialize(state: MinesweeperState, firstIndex: Int): MinesweeperState {
    val safeZone = (neighbors(firstIndex, state.width, state.height) + firstIndex).toSet()
    val all = state.cells.indices.toList()
    var candidates = all.filterNot(safeZone::contains)
    if (candidates.size < state.mineCount) candidates = all.filter { it != firstIndex }
    val mines = candidates.shuffled(random).take(state.mineCount).toSet()
    val cells = List(state.cells.size) { index ->
      val originalVisibility = state.cells[index].visibility
      MinesweeperCell(
        isMine = index in mines,
        adjacentMines = neighbors(index, state.width, state.height).count(mines::contains),
        visibility = originalVisibility,
      )
    }
    return state.copy(cells = cells, initialized = true)
  }
}

internal fun neighbors(index: Int, width: Int, height: Int): List<Int> {
  val row = index / width
  val column = index % width
  return buildList {
    for (rowOffset in -1..1) {
      for (columnOffset in -1..1) {
        if (rowOffset == 0 && columnOffset == 0) continue
        val nextRow = row + rowOffset
        val nextColumn = column + columnOffset
        if (nextRow in 0 until height && nextColumn in 0 until width) {
          add(nextRow * width + nextColumn)
        }
      }
    }
  }
}
