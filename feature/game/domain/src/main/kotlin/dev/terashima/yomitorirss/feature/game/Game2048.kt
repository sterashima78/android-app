package dev.terashima.yomitorirss.feature.game

import kotlin.random.Random

private const val GAME_2048_SIZE = 4
private const val GAME_2048_CELL_COUNT = GAME_2048_SIZE * GAME_2048_SIZE

enum class Game2048Direction { UP, DOWN, LEFT, RIGHT }

data class Game2048State(
  val tiles: List<Int>,
  val score: Int = 0,
) {
  init {
    require(tiles.size == GAME_2048_CELL_COUNT) { "2048の盤面は16マス必要です" }
    require(tiles.all { it == 0 || (it > 0 && it and (it - 1) == 0) }) {
      "2048のタイルは0または2の累乗である必要があります"
    }
  }

  val hasWon: Boolean
    get() = tiles.any { it >= 2048 }

  val isGameOver: Boolean
    get() {
      if (tiles.any { it == 0 }) return false
      for (row in 0 until GAME_2048_SIZE) {
        for (column in 0 until GAME_2048_SIZE) {
          val index = row * GAME_2048_SIZE + column
          if (column + 1 < GAME_2048_SIZE && tiles[index] == tiles[index + 1]) return false
          if (row + 1 < GAME_2048_SIZE && tiles[index] == tiles[index + GAME_2048_SIZE]) return false
        }
      }
      return true
    }
}

class Game2048(
  private val random: Random = Random.Default,
) {
  fun newGame(): Game2048State {
    var state = Game2048State(List(GAME_2048_CELL_COUNT) { 0 })
    state = spawnTile(state)
    return spawnTile(state)
  }

  fun move(state: Game2048State, direction: Game2048Direction): Game2048State {
    if (state.isGameOver) return state

    val next = MutableList(GAME_2048_CELL_COUNT) { 0 }
    var gainedScore = 0

    for (lineIndex in 0 until GAME_2048_SIZE) {
      val indices = lineIndices(lineIndex, direction)
      val source = indices.map(state.tiles::get)
      val (merged, score) = mergeLine(source)
      gainedScore += score
      indices.forEachIndexed { position, boardIndex -> next[boardIndex] = merged[position] }
    }

    if (next == state.tiles) return state
    return spawnTile(Game2048State(next, state.score + gainedScore))
  }

  private fun spawnTile(state: Game2048State): Game2048State {
    val empty = state.tiles.indices.filter { state.tiles[it] == 0 }
    if (empty.isEmpty()) return state
    val target = empty[random.nextInt(empty.size)]
    val value = if (random.nextInt(10) == 0) 4 else 2
    val next = state.tiles.toMutableList().also { it[target] = value }
    return state.copy(tiles = next)
  }
}

internal fun merge2048Line(values: List<Int>): Pair<List<Int>, Int> = mergeLine(values)

private fun mergeLine(values: List<Int>): Pair<List<Int>, Int> {
  require(values.size == GAME_2048_SIZE)
  val compact = values.filter { it != 0 }
  val result = mutableListOf<Int>()
  var score = 0
  var index = 0
  while (index < compact.size) {
    if (index + 1 < compact.size && compact[index] == compact[index + 1]) {
      val merged = compact[index] * 2
      result += merged
      score += merged
      index += 2
    } else {
      result += compact[index]
      index += 1
    }
  }
  while (result.size < GAME_2048_SIZE) result += 0
  return result to score
}

private fun lineIndices(line: Int, direction: Game2048Direction): List<Int> =
  when (direction) {
    Game2048Direction.LEFT -> (0 until GAME_2048_SIZE).map { column -> line * GAME_2048_SIZE + column }
    Game2048Direction.RIGHT -> (GAME_2048_SIZE - 1 downTo 0).map { column -> line * GAME_2048_SIZE + column }
    Game2048Direction.UP -> (0 until GAME_2048_SIZE).map { row -> row * GAME_2048_SIZE + line }
    Game2048Direction.DOWN -> (GAME_2048_SIZE - 1 downTo 0).map { row -> row * GAME_2048_SIZE + line }
  }
