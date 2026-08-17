package dev.terashima.yomitorirss.feature.game

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpiderTest {
  @Test
  fun `初期配札は104枚を54枚の場札と50枚の山札へ分ける`() {
    SpiderDifficulty.entries.forEach { difficulty ->
      val state = SpiderGameFactory(Random(1)).create(difficulty)

      assertEquals(listOf(6, 6, 6, 6, 5, 5, 5, 5, 5, 5), state.tableau.map { it.size })
      assertEquals(50, state.stock.size)
      assertEquals(104, state.tableau.sumOf { it.size } + state.stock.size)
      assertEquals(10, state.tableau.sumOf { pile -> pile.count { it.faceUp } })
      assertEquals(5, state.stock.size / 10)
    }
  }

  @Test
  fun `難易度ごとに使用するスート数が変わる`() {
    val oneSuit = SpiderGameFactory(Random(2)).create(SpiderDifficulty.ONE_SUIT)
    val twoSuits = SpiderGameFactory(Random(2)).create(SpiderDifficulty.TWO_SUITS)
    val fourSuits = SpiderGameFactory(Random(2)).create(SpiderDifficulty.FOUR_SUITS)

    fun suits(state: SpiderGameState) =
      (state.stock + state.tableau.flatten().map { it.card }).map { it.suit }.toSet()

    assertEquals(setOf(CardSuit.SPADES), suits(oneSuit))
    assertEquals(setOf(CardSuit.SPADES, CardSuit.HEARTS), suits(twoSuits))
    assertEquals(CardSuit.entries.toSet(), suits(fourSuits))
  }

  @Test
  fun `同一スートの降順列は別スートの1つ上のカードにも移動できる`() {
    val state = stateWithTableau(
      first = listOf(
        SpiderTableauCard(PlayingCard(CardSuit.SPADES, 9), faceUp = false),
        SpiderTableauCard(PlayingCard(CardSuit.SPADES, 5), faceUp = true),
        SpiderTableauCard(PlayingCard(CardSuit.SPADES, 4), faceUp = true),
      ),
      second = listOf(SpiderTableauCard(PlayingCard(CardSuit.HEARTS, 6), faceUp = true)),
      selection = SpiderSelection(0, 1),
    )

    val moved = state.moveSelectedToTableau(1)

    assertEquals(listOf(9), moved.tableau[0].map { it.card.rank })
    assertTrue(moved.tableau[0].single().faceUp)
    assertEquals(listOf(6, 5, 4), moved.tableau[1].map { it.card.rank })
    assertEquals(1, moved.moves)
  }

  @Test
  fun `異なるスートを含む列はまとめて選択できない`() {
    val state = stateWithTableau(
      first = listOf(
        SpiderTableauCard(PlayingCard(CardSuit.SPADES, 5), faceUp = true),
        SpiderTableauCard(PlayingCard(CardSuit.HEARTS, 4), faceUp = true),
      ),
    )

    assertEquals(state, state.selectTableau(0, 0))
  }

  @Test
  fun `選択した連続列の合法な移動先だけを列挙する`() {
    val source = listOf(
      SpiderTableauCard(PlayingCard(CardSuit.SPADES, 5), faceUp = true),
      SpiderTableauCard(PlayingCard(CardSuit.SPADES, 4), faceUp = true),
    )
    val valid = listOf(SpiderTableauCard(PlayingCard(CardSuit.HEARTS, 6), faceUp = true))
    val invalid = listOf(SpiderTableauCard(PlayingCard(CardSuit.CLUBS, 7), faceUp = true))
    val filler = listOf(SpiderTableauCard(PlayingCard(CardSuit.CLUBS, 10), faceUp = true))
    val state = SpiderGameState(
      difficulty = SpiderDifficulty.FOUR_SUITS,
      stock = emptyList(),
      tableau = listOf(source, valid, invalid, emptyList()) + List(6) { filler },
      completedRuns = emptyList(),
      selection = SpiderSelection(0, 0),
      moves = 0,
    )

    assertEquals(setOf(1, 3), state.validTableauTargets())
    assertEquals(2, state.selectedCardCount())
  }

  @Test
  fun `KからAの同一スート列が完成すると自動回収して伏せ札を表にする`() {
    val target = (13 downTo 2).map { rank ->
      SpiderTableauCard(PlayingCard(CardSuit.SPADES, rank), faceUp = true)
    }
    val state = stateWithTableau(
      first = listOf(
        SpiderTableauCard(PlayingCard(CardSuit.HEARTS, 9), faceUp = false),
        SpiderTableauCard(PlayingCard(CardSuit.SPADES, 1), faceUp = true),
      ),
      second = target,
      selection = SpiderSelection(0, 1),
    )

    val moved = state.moveSelectedToTableau(1)

    assertEquals(listOf(CardSuit.SPADES), moved.completedRuns)
    assertTrue(moved.tableau[1].isEmpty())
    assertTrue(moved.tableau[0].single().faceUp)
  }

  @Test
  fun `山札は10枚ずつ各列へ1枚配る`() {
    val state = SpiderGameFactory(Random(3)).create()

    val dealt = state.dealStock()

    assertEquals(40, dealt.stock.size)
    assertTrue(dealt.tableau.zip(state.tableau).all { (after, before) -> after.size == before.size + 1 })
    assertTrue(dealt.tableau.all { it.last().faceUp })
    assertEquals(1, dealt.moves)
  }

  @Test
  fun `空列がある間は山札を配れない`() {
    val initial = SpiderGameFactory(Random(4)).create()
    val tableau = initial.tableau.toMutableList().also { it[0] = emptyList() }
    val state = initial.copy(tableau = tableau)

    assertFalse(state.canDealStock)
    assertEquals(state, state.dealStock())
  }

  @Test
  fun `8組完成するとクリアになる`() {
    val state = stateWithTableau(
      completedRuns = List(8) { CardSuit.SPADES },
    )

    assertTrue(state.isWon)
    assertFalse(state.canDealStock)
  }

  private fun stateWithTableau(
    first: List<SpiderTableauCard> = listOf(SpiderTableauCard(PlayingCard(CardSuit.SPADES, 7), true)),
    second: List<SpiderTableauCard> = listOf(SpiderTableauCard(PlayingCard(CardSuit.SPADES, 8), true)),
    selection: SpiderSelection? = null,
    completedRuns: List<CardSuit> = emptyList(),
  ): SpiderGameState = SpiderGameState(
    difficulty = SpiderDifficulty.FOUR_SUITS,
    stock = emptyList(),
    tableau = listOf(first, second) + List(8) {
      listOf(SpiderTableauCard(PlayingCard(CardSuit.CLUBS, 10), faceUp = true))
    },
    completedRuns = completedRuns,
    selection = selection,
    moves = 0,
  )
}
