package dev.terashima.yomitorirss.feature.game

import kotlin.random.Random

private const val NONOGRAM_SIZE = 5

enum class NonogramCellState { UNKNOWN, FILLED, MARKED }

data class NonogramPuzzle(
  val width: Int,
  val height: Int,
  val solution: List<Boolean>,
) {
  init {
    require(width > 0 && height > 0)
    require(solution.size == width * height)
  }

  val rowClues: List<List<Int>>
    get() = (0 until height).map { row ->
      nonogramClues((0 until width).map { column -> solution[row * width + column] })
    }

  val columnClues: List<List<Int>>
    get() = (0 until width).map { column ->
      nonogramClues((0 until height).map { row -> solution[row * width + column] })
    }
}

data class NonogramGameState(
  val puzzle: NonogramPuzzle,
  val cells: List<NonogramCellState>,
) {
  val isCompleted: Boolean
    get() = cells.indices.all { index ->
      (cells[index] == NonogramCellState.FILLED) == puzzle.solution[index]
    }
}

class NonogramPuzzleFactory(
  private val random: Random = Random.Default,
) {
  fun create(): NonogramPuzzle {
    val rows = basePatterns[random.nextInt(basePatterns.size)]
    var cells = rows.flatMap { row -> row.map { it == '#' } }
    if (random.nextBoolean()) cells = flipHorizontal(cells)
    if (random.nextBoolean()) cells = flipVertical(cells)
    if (random.nextBoolean()) cells = transpose(cells)
    return NonogramPuzzle(NONOGRAM_SIZE, NONOGRAM_SIZE, cells)
  }

  private fun flipHorizontal(cells: List<Boolean>): List<Boolean> =
    List(cells.size) { index ->
      val row = index / NONOGRAM_SIZE
      val column = index % NONOGRAM_SIZE
      cells[row * NONOGRAM_SIZE + (NONOGRAM_SIZE - 1 - column)]
    }

  private fun flipVertical(cells: List<Boolean>): List<Boolean> =
    List(cells.size) { index ->
      val row = index / NONOGRAM_SIZE
      val column = index % NONOGRAM_SIZE
      cells[(NONOGRAM_SIZE - 1 - row) * NONOGRAM_SIZE + column]
    }

  private fun transpose(cells: List<Boolean>): List<Boolean> =
    List(cells.size) { index ->
      val row = index / NONOGRAM_SIZE
      val column = index % NONOGRAM_SIZE
      cells[column * NONOGRAM_SIZE + row]
    }
}

fun NonogramPuzzle.newGameState(): NonogramGameState =
  NonogramGameState(this, List(width * height) { NonogramCellState.UNKNOWN })

fun NonogramGameState.fill(index: Int): NonogramGameState = update(index) { current ->
  if (current == NonogramCellState.FILLED) NonogramCellState.UNKNOWN else NonogramCellState.FILLED
}

fun NonogramGameState.mark(index: Int): NonogramGameState = update(index) { current ->
  if (current == NonogramCellState.MARKED) NonogramCellState.UNKNOWN else NonogramCellState.MARKED
}

private fun NonogramGameState.update(
  index: Int,
  transform: (NonogramCellState) -> NonogramCellState,
): NonogramGameState {
  if (index !in cells.indices || isCompleted) return this
  val next = cells.toMutableList().also { it[index] = transform(it[index]) }
  return copy(cells = next)
}

internal fun nonogramClues(line: List<Boolean>): List<Int> {
  val clues = mutableListOf<Int>()
  var run = 0
  line.forEach { filled ->
    if (filled) {
      run += 1
    } else if (run > 0) {
      clues += run
      run = 0
    }
  }
  if (run > 0) clues += run
  return if (clues.isEmpty()) listOf(0) else clues
}

private val basePatterns = listOf(
  listOf(".###.", "#####", "#####", ".###.", "..#.."),
  listOf("#...#", ".#.#.", "..#..", ".#.#.", "#...#"),
  listOf("..#..", ".###.", "#####", ".###.", "..#.."),
  listOf(".###.", "#...#", "#.#.#", "#...#", ".###."),
  listOf("#.#.#", "#####", ".###.", "..#..", "..#.."),
  listOf("##.##", "#####", ".###.", ".###.", "..#.."),
)
