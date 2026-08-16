package dev.terashima.yomitorirss.feature.game

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KlondikeTest {
  @Test
  fun `初期配札は52枚を重複なく分配する`() {
    val state = KlondikeGameFactory(Random(1)).create()

    assertEquals((1..7).toList(), state.tableau.map { it.size })
    assertEquals(24, state.stock.size)
    assertTrue(state.waste.isEmpty())

    val cards = state.stock + state.tableau.flatten().map { it.card }
    assertEquals(52, cards.size)
    assertEquals(52, cards.toSet().size)
  }

  @Test
  fun `山札が空になると捨て札を裏返して再利用する`() {
    var state = KlondikeGameFactory(Random(1)).create()
    repeat(24) { state = state.drawStock() }

    assertTrue(state.stock.isEmpty())
    assertEquals(24, state.waste.size)

    val firstDrawn = state.waste.first()
    state = state.drawStock()

    assertEquals(24, state.stock.size)
    assertTrue(state.waste.isEmpty())
    assertEquals(firstDrawn, state.stock.last())
  }

  @Test
  fun `Aは同じスートの空の組札へ移動できる`() {
    val ace = PlayingCard(CardSuit.HEARTS, 1)
    val state = emptyState(
      waste = listOf(ace),
      selection = KlondikeSelection.Waste,
    ).moveSelectedToFoundation(CardSuit.HEARTS)

    assertTrue(state.waste.isEmpty())
    assertEquals(listOf(ace), state.foundations.getValue(CardSuit.HEARTS))
  }

  @Test
  fun `場札は赤黒交互の降順だけ移動できる`() {
    val blackSeven = PlayingCard(CardSuit.SPADES, 7)
    val redSix = PlayingCard(CardSuit.HEARTS, 6)
    val blackFive = PlayingCard(CardSuit.CLUBS, 5)
    val state = emptyState(
      tableau = listOf(
        listOf(TableauCard(blackSeven, true)),
        listOf(TableauCard(redSix, true), TableauCard(blackFive, true)),
        emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
      ),
      selection = KlondikeSelection.Tableau(1, 0),
    )

    val moved = state.moveSelectedToTableau(0)

    assertNotEquals(state, moved)
    assertEquals(
      listOf(blackSeven, redSix, blackFive),
      moved.tableau[0].map { it.card },
    )
  }

  @Test
  fun `同色の場札列は移動できない`() {
    val blackSeven = PlayingCard(CardSuit.SPADES, 7)
    val redSix = PlayingCard(CardSuit.HEARTS, 6)
    val redFive = PlayingCard(CardSuit.DIAMONDS, 5)
    val state = emptyState(
      tableau = listOf(
        listOf(TableauCard(blackSeven, true)),
        listOf(TableauCard(redSix, true), TableauCard(redFive, true)),
        emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
      ),
      selection = KlondikeSelection.Tableau(1, 0),
    )

    assertEquals(state, state.moveSelectedToTableau(0))
  }

  private fun emptyState(
    waste: List<PlayingCard> = emptyList(),
    tableau: List<List<TableauCard>> = List(7) { emptyList() },
    selection: KlondikeSelection? = null,
  ) = KlondikeGameState(
    stock = emptyList(),
    waste = waste,
    foundations = CardSuit.entries.associateWith { emptyList() },
    tableau = tableau,
    selection = selection,
    moves = 0,
  )
}
