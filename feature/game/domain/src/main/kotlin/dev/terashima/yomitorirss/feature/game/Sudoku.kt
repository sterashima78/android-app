package dev.terashima.yomitorirss.feature.game

import kotlin.random.Random

private const val BOARD_SIZE = 9
private const val CELL_COUNT = BOARD_SIZE * BOARD_SIZE

private val basePuzzle = listOf(
  5, 3, 0, 0, 7, 0, 0, 0, 0,
  6, 0, 0, 1, 9, 5, 0, 0, 0,
  0, 9, 8, 0, 0, 0, 0, 6, 0,
  8, 0, 0, 0, 6, 0, 0, 0, 3,
  4, 0, 0, 8, 0, 3, 0, 0, 1,
  7, 0, 0, 0, 2, 0, 0, 0, 6,
  0, 6, 0, 0, 0, 0, 2, 8, 0,
  0, 0, 0, 4, 1, 9, 0, 0, 5,
  0, 0, 0, 0, 8, 0, 0, 7, 9,
)

private val baseSolution = listOf(
  5, 3, 4, 6, 7, 8, 9, 1, 2,
  6, 7, 2, 1, 9, 5, 3, 4, 8,
  1, 9, 8, 3, 4, 2, 5, 6, 7,
  8, 5, 9, 7, 6, 1, 4, 2, 3,
  4, 2, 6, 8, 5, 3, 7, 9, 1,
  7, 1, 3, 9, 2, 4, 8, 5, 6,
  9, 6, 1, 5, 3, 7, 2, 8, 4,
  2, 8, 7, 4, 1, 9, 6, 3, 5,
  3, 4, 5, 2, 8, 6, 1, 7, 9,
)

data class SudokuPuzzle(
  val cells: List<Int>,
  val solution: List<Int>,
) {
  init {
    require(cells.size == CELL_COUNT) { "数独の盤面は81マス必要です" }
    require(solution.size == CELL_COUNT) { "数独の解は81マス必要です" }
    require(cells.all { it in 0..9 }) { "盤面の値は0から9である必要があります" }
    require(solution.all { it in 1..9 }) { "解の値は1から9である必要があります" }
    require(cells.indices.all { cells[it] == 0 || cells[it] == solution[it] }) {
      "問題の固定値と解が一致していません"
    }
    require(isValidSudokuSolution(solution)) { "数独の解が不正です" }
  }

  fun isGiven(index: Int): Boolean = index in cells.indices && cells[index] != 0
}

data class SudokuGameState(
  val puzzle: SudokuPuzzle,
  val entries: List<Int>,
  val selectedIndex: Int?,
  val mistakes: Int,
) {
  val isCompleted: Boolean
    get() = entries == puzzle.solution
}

class SudokuPuzzleFactory(
  private val random: Random = Random.Default,
) {
  fun create(): SudokuPuzzle {
    val digitMap = (1..9).shuffled(random)
    val rowOrder = randomizedUnitOrder()
    val columnOrder = randomizedUnitOrder()
    val transpose = random.nextBoolean()

    fun transformed(source: List<Int>): List<Int> = List(CELL_COUNT) { targetIndex ->
      val targetRow = targetIndex / BOARD_SIZE
      val targetColumn = targetIndex % BOARD_SIZE
      val sourceRow = if (transpose) columnOrder[targetColumn] else rowOrder[targetRow]
      val sourceColumn = if (transpose) rowOrder[targetRow] else columnOrder[targetColumn]
      val value = source[sourceRow * BOARD_SIZE + sourceColumn]
      if (value == 0) 0 else digitMap[value - 1]
    }

    return SudokuPuzzle(
      cells = transformed(basePuzzle),
      solution = transformed(baseSolution),
    )
  }

  private fun randomizedUnitOrder(): List<Int> =
    (0 until 3).shuffled(random).flatMap { group ->
      (0 until 3).shuffled(random).map { offset -> group * 3 + offset }
    }
}

fun SudokuPuzzle.newGameState(): SudokuGameState {
  val firstEditable = cells.indexOfFirst { it == 0 }.takeIf { it >= 0 }
  return SudokuGameState(
    puzzle = this,
    entries = cells,
    selectedIndex = firstEditable,
    mistakes = 0,
  )
}

fun SudokuGameState.selectCell(index: Int): SudokuGameState =
  if (index in 0 until CELL_COUNT) copy(selectedIndex = index) else this

fun SudokuGameState.enterNumber(value: Int): SudokuGameState {
  val index = selectedIndex ?: return this
  if (value !in 1..9 || puzzle.isGiven(index) || isCompleted) return this
  if (puzzle.solution[index] != value) return copy(mistakes = mistakes + 1)

  val nextEntries = entries.toMutableList().also { it[index] = value }
  return copy(entries = nextEntries)
}

fun SudokuGameState.clearSelectedCell(): SudokuGameState {
  val index = selectedIndex ?: return this
  if (puzzle.isGiven(index) || entries[index] == 0 || isCompleted) return this
  val nextEntries = entries.toMutableList().also { it[index] = 0 }
  return copy(entries = nextEntries)
}

internal fun isValidSudokuSolution(values: List<Int>): Boolean {
  if (values.size != CELL_COUNT || values.any { it !in 1..9 }) return false
  val expected = (1..9).toSet()

  for (index in 0 until BOARD_SIZE) {
    val row = (0 until BOARD_SIZE).map { column -> values[index * BOARD_SIZE + column] }.toSet()
    val column = (0 until BOARD_SIZE).map { rowIndex -> values[rowIndex * BOARD_SIZE + index] }.toSet()
    if (row != expected || column != expected) return false
  }

  for (boxRow in 0 until 3) {
    for (boxColumn in 0 until 3) {
      val box = buildSet {
        for (rowOffset in 0 until 3) {
          for (columnOffset in 0 until 3) {
            val row = boxRow * 3 + rowOffset
            val column = boxColumn * 3 + columnOffset
            add(values[row * BOARD_SIZE + column])
          }
        }
      }
      if (box != expected) return false
    }
  }

  return true
}
