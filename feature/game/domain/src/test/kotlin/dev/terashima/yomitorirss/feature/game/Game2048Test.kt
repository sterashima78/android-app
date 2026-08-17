package dev.terashima.yomitorirss.feature.game

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Game2048Test {
  @Test
  fun `同じ値は一手で一度だけ結合する`() {
    val (tiles, score) = merge2048Line(listOf(2, 2, 2, 2))
    assertEquals(listOf(4, 4, 0, 0), tiles)
    assertEquals(8, score)
  }

  @Test
  fun `新しいゲームは2枚のタイルで開始する`() {
    val state = Game2048(Random(0)).newGame()
    assertEquals(2, state.tiles.count { it != 0 })
    assertTrue(state.tiles.filter { it != 0 }.all { it == 2 || it == 4 })
  }

  @Test
  fun `移動で結合した値をスコアへ加算する`() {
    val game = Game2048(Random(1))
    val state = Game2048State(listOf(2, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
    val next = game.move(state, Game2048Direction.LEFT)
    assertEquals(4, next.tiles[0])
    assertEquals(4, next.score)
    assertEquals(2, next.tiles.count { it != 0 })
  }

  @Test
  fun `右移動と下移動でも移動方向側へ結合する`() {
    val horizontal = Game2048State(listOf(2, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
    val vertical = Game2048State(listOf(2, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
    val right = Game2048(Random(10)).move(horizontal, Game2048Direction.RIGHT)
    val down = Game2048(Random(11)).move(vertical, Game2048Direction.DOWN)
    assertEquals(4, right.tiles[3])
    assertEquals(4, down.tiles[12])
    assertEquals(4, right.score)
    assertEquals(4, down.score)
  }

  @Test
  fun `動かない入力では新しいタイルを追加しない`() {
    val game = Game2048(Random(2))
    val state = Game2048State(listOf(2, 4, 8, 16, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
    assertEquals(state, game.move(state, Game2048Direction.LEFT))
  }

  @Test
  fun `空きも結合候補もない盤面はゲームオーバーになる`() {
    val state = Game2048State(listOf(2, 4, 2, 4, 4, 2, 4, 2, 2, 4, 2, 4, 4, 2, 4, 2))
    assertTrue(state.isGameOver)
    assertFalse(state.hasWon)
  }

  @Test
  fun `結合時は2枚の移動元と結合先を遷移情報へ含める`() {
    val state = Game2048State(listOf(2, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
    val result = Game2048(Random(3)).moveWithTransition(state, Game2048Direction.LEFT)

    val merged = result.movements.filter { it.isMerge }
    assertTrue(result.changed)
    assertEquals(4, result.scoreDelta)
    assertEquals(setOf(0, 1), merged.map { it.fromIndex }.toSet())
    assertEquals(setOf(0), merged.map { it.toIndex }.toSet())
    assertEquals(listOf(2, 2), merged.map { it.value })
    assertNotNull(result.spawnedIndex)
  }

  @Test
  fun `空白を詰める移動は元と先の位置を遷移情報へ含める`() {
    val state = Game2048State(listOf(0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
    val result = Game2048(Random(4)).moveWithTransition(state, Game2048Direction.LEFT)

    assertTrue(result.changed)
    assertTrue(result.movements.any {
      it.fromIndex == 1 && it.toIndex == 0 && it.value == 2 && !it.isMerge
    })
    assertNotNull(result.spawnedIndex)
  }

  @Test
  fun `動かない入力も方向付きの結果として返す`() {
    val state = Game2048State(listOf(2, 4, 8, 16, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
    val result = Game2048(Random(5)).moveWithTransition(state, Game2048Direction.LEFT)

    assertFalse(result.changed)
    assertEquals(Game2048Direction.LEFT, result.direction)
    assertEquals(state, result.state)
    assertTrue(result.movements.isEmpty())
    assertNull(result.spawnedIndex)
    assertEquals(0, result.scoreDelta)
  }
}
