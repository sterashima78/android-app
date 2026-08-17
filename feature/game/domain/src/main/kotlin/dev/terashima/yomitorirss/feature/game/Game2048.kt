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

data class Game2048TileMovement(
  val fromIndex: Int,
  val toIndex: Int,
  val value: Int,
  val isMerge: Boolean,
)

data class Game2048MoveResult(
  val state: Game2048State,
  val direction: Game2048Direction,
  val movements: List<Game2048TileMovement>,
  val spawnedIndex: Int?,
  val scoreDelta: Int,
) {
  val changed: Boolean
    get() = movements.any { it.fromIndex != it.toIndex || it.isMerge }
}

class Game2048(
  private val random: Random = Random.Default,
) {
  fun newGame(): Game2048State {
    var state = Game2048State(List(GAME_2048_CELL_COUNT) { 0 })
    state = spawnTile(state)
    return spawnTile(state)
  }

  fun move(state: Game2048State, direction: Game2048Direction): Game2048State =
    moveWithTransition(state, direction).state

  fun moveWithTransition(
    state: Game2048State,
    direction: Game2048Direction,
  ): Game2048MoveResult {
    if (state.isGameOver) return unchangedMove(state, direction)

    val next = MutableList(GAME_2048_CELL_COUNT) { 0 }
    val movements = mutableListOf<Game2048TileMovement>()
    var gainedScore = 0

    for (lineIndex in 0 until GAME_2048_SIZE) {
      val indices = lineIndices(lineIndex, direction)
      val resolved = resolveLine(indices.map(state.tiles::get))
      gainedScore += resolved.score
      indices.forEachIndexed { position, boardIndex ->
        next[boardIndex] = resolved.values[position]
      }
      resolved.movements.forEach { movement ->
        movements += Game2048TileMovement(
          fromIndex = indices[movement.fromPosition],
          toIndex = indices[movement.toPosition],
          value = movement.value,
          isMerge = movement.isMerge,
        )
      }
    }

    if (next == state.tiles) return unchangedMove(state, direction)

    val beforeSpawn = Game2048State(next, state.score + gainedScore)
    val spawned = spawnTileWithIndex(beforeSpawn)
    return Game2048MoveResult(
      state = spawned.state,
      direction = direction,
      movements = movements,
      spawnedIndex = spawned.index,
      scoreDelta = gainedScore,
    )
  }

  private fun unchangedMove(
    state: Game2048State,
    direction: Game2048Direction,
  ) = Game2048MoveResult(
    state = state,
    direction = direction,
    movements = emptyList(),
    spawnedIndex = null,
    scoreDelta = 0,
  )

  private fun spawnTile(state: Game2048State): Game2048State = spawnTileWithIndex(state).state

  private fun spawnTileWithIndex(state: Game2048State): Spawned2048Tile {
    val empty = state.tiles.indices.filter { state.tiles[it] == 0 }
    if (empty.isEmpty()) return Spawned2048Tile(state, null)
    val target = empty[random.nextInt(empty.size)]
    val value = if (random.nextInt(10) == 0) 4 else 2
    val next = state.tiles.toMutableList().also { it[target] = value }
    return Spawned2048Tile(state.copy(tiles = next), target)
  }
}

internal fun merge2048Line(values: List<Int>): Pair<List<Int>, Int> {
  val resolved = resolveLine(values)
  return resolved.values to resolved.score
}

private data class Indexed2048Tile(
  val position: Int,
  val value: Int,
)

private data class Line2048Movement(
  val fromPosition: Int,
  val toPosition: Int,
  val value: Int,
  val isMerge: Boolean,
)

private data class Resolved2048Line(
  val values: List<Int>,
  val score: Int,
  val movements: List<Line2048Movement>,
)

private data class Spawned2048Tile(
  val state: Game2048State,
  val index: Int?,
)

private fun resolveLine(values: List<Int>): Resolved2048Line {
  require(values.size == GAME_2048_SIZE)
  val compact = values.mapIndexedNotNull { position, value ->
    value.takeIf { it != 0 }?.let { Indexed2048Tile(position, it) }
  }
  val result = MutableList(GAME_2048_SIZE) { 0 }
  val movements = mutableListOf<Line2048Movement>()
  var score = 0
  var sourceIndex = 0
  var targetPosition = 0

  while (sourceIndex < compact.size) {
    val current = compact[sourceIndex]
    val next = compact.getOrNull(sourceIndex + 1)
    if (next != null && current.value == next.value) {
      val merged = current.value * 2
      result[targetPosition] = merged
      score += merged
      movements += Line2048Movement(current.position, targetPosition, current.value, isMerge = true)
      movements += Line2048Movement(next.position, targetPosition, next.value, isMerge = true)
      sourceIndex += 2
    } else {
      result[targetPosition] = current.value
      movements += Line2048Movement(current.position, targetPosition, current.value, isMerge = false)
      sourceIndex += 1
    }
    targetPosition += 1
  }

  return Resolved2048Line(result, score, movements)
}

private fun lineIndices(line: Int, direction: Game2048Direction): List<Int> =
  when (direction) {
    Game2048Direction.LEFT -> (0 until GAME_2048_SIZE).map { column -> line * GAME_2048_SIZE + column }
    Game2048Direction.RIGHT -> (GAME_2048_SIZE - 1 downTo 0).map { column -> line * GAME_2048_SIZE + column }
    Game2048Direction.UP -> (0 until GAME_2048_SIZE).map { row -> row * GAME_2048_SIZE + line }
    Game2048Direction.DOWN -> (GAME_2048_SIZE - 1 downTo 0).map { row -> row * GAME_2048_SIZE + line }
  }
