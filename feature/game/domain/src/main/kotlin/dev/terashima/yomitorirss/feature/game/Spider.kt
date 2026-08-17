package dev.terashima.yomitorirss.feature.game

import kotlin.random.Random

enum class SpiderDifficulty {
  ONE_SUIT,
  TWO_SUITS,
  FOUR_SUITS,
}

data class SpiderTableauCard(
  val card: PlayingCard,
  val faceUp: Boolean,
)

data class SpiderSelection(
  val pileIndex: Int,
  val cardIndex: Int,
)

data class SpiderGameState(
  val difficulty: SpiderDifficulty,
  val stock: List<PlayingCard>,
  val tableau: List<List<SpiderTableauCard>>,
  val completedRuns: List<CardSuit>,
  val selection: SpiderSelection?,
  val moves: Int,
) {
  init {
    require(tableau.size == 10) { "スパイダーソリティアの場札は10列必要です" }
    require(stock.size % 10 == 0) { "スパイダーソリティアの山札は10枚単位である必要があります" }
    require(completedRuns.size <= 8) { "完成列は8組以下である必要があります" }
  }

  val isWon: Boolean
    get() = completedRuns.size == 8

  val canDealStock: Boolean
    get() = stock.size >= 10 && tableau.none { it.isEmpty() } && !isWon
}

class SpiderGameFactory(
  private val random: Random = Random.Default,
) {
  fun create(difficulty: SpiderDifficulty = SpiderDifficulty.ONE_SUIT): SpiderGameState {
    val suits = difficulty.suits()
    val copiesPerSuit = 8 / suits.size
    val deck = buildList {
      repeat(copiesPerSuit) {
        suits.forEach { suit ->
          (1..13).forEach { rank -> add(PlayingCard(suit, rank)) }
        }
      }
    }.shuffled(random)

    var cursor = 0
    val tableau = (0 until 10).map { pileIndex ->
      val pileSize = if (pileIndex < 4) 6 else 5
      List(pileSize) { cardIndex ->
        SpiderTableauCard(
          card = deck[cursor++],
          faceUp = cardIndex == pileSize - 1,
        )
      }
    }

    return SpiderGameState(
      difficulty = difficulty,
      stock = deck.drop(cursor),
      tableau = tableau,
      completedRuns = emptyList(),
      selection = null,
      moves = 0,
    )
  }
}

fun SpiderGameState.selectTableau(pileIndex: Int, cardIndex: Int): SpiderGameState {
  if (isWon) return this
  val pile = tableau.getOrNull(pileIndex) ?: return this
  val selectedCard = pile.getOrNull(cardIndex) ?: return this
  if (!selectedCard.faceUp || movableRun(pile, cardIndex) == null) return this

  val next = SpiderSelection(pileIndex, cardIndex)
  return copy(selection = if (selection == next) null else next)
}

fun SpiderGameState.selectedCardCount(): Int {
  val selected = selection ?: return 0
  return movableRun(tableau.getOrNull(selected.pileIndex).orEmpty(), selected.cardIndex)?.size ?: 0
}

fun SpiderGameState.validTableauTargets(): Set<Int> {
  val selected = selection ?: return emptySet()
  val moving = movableRun(tableau.getOrNull(selected.pileIndex).orEmpty(), selected.cardIndex) ?: return emptySet()

  return tableau.indices.filterTo(mutableSetOf()) { targetPileIndex ->
    if (targetPileIndex == selected.pileIndex) {
      false
    } else {
      val targetTop = tableau[targetPileIndex].lastOrNull()
      targetTop == null || (targetTop.faceUp && targetTop.card.rank == moving.first().card.rank + 1)
    }
  }
}

fun SpiderGameState.moveSelectedToTableau(targetPileIndex: Int): SpiderGameState {
  val selected = selection ?: return this
  if (selected.pileIndex == targetPileIndex) return copy(selection = null)
  val source = tableau.getOrNull(selected.pileIndex) ?: return this
  val moving = movableRun(source, selected.cardIndex) ?: return this
  val target = tableau.getOrNull(targetPileIndex) ?: return this
  val targetTop = target.lastOrNull()
  if (targetTop != null && (!targetTop.faceUp || targetTop.card.rank != moving.first().card.rank + 1)) {
    return this
  }

  val nextTableau = tableau.map { it.toMutableList() }.toMutableList()
  val sourcePile = nextTableau[selected.pileIndex]
  sourcePile.subList(selected.cardIndex, sourcePile.size).clear()
  flipExposedTop(sourcePile)
  nextTableau[targetPileIndex].addAll(moving)

  return copy(
    tableau = nextTableau.map { it.toList() },
    selection = null,
    moves = moves + 1,
  ).collectCompletedRuns()
}

fun SpiderGameState.dealStock(): SpiderGameState {
  if (!canDealStock) return this
  val dealt = stock.takeLast(10)
  val nextTableau = tableau.map { it.toMutableList() }.toMutableList()
  dealt.forEachIndexed { pileIndex, card ->
    nextTableau[pileIndex].add(SpiderTableauCard(card, faceUp = true))
  }

  return copy(
    stock = stock.dropLast(10),
    tableau = nextTableau.map { it.toList() },
    selection = null,
    moves = moves + 1,
  ).collectCompletedRuns()
}

private fun movableRun(
  pile: List<SpiderTableauCard>,
  cardIndex: Int,
): List<SpiderTableauCard>? {
  if (cardIndex !in pile.indices) return null
  val run = pile.drop(cardIndex)
  if (run.isEmpty() || run.any { !it.faceUp }) return null
  val valid = run.zipWithNext().all { (lower, upper) ->
    lower.card.suit == upper.card.suit && lower.card.rank == upper.card.rank + 1
  }
  return run.takeIf { valid }
}

private fun SpiderGameState.collectCompletedRuns(): SpiderGameState {
  val nextTableau = tableau.map { it.toMutableList() }.toMutableList()
  val nextCompleted = completedRuns.toMutableList()
  var changed: Boolean

  do {
    changed = false
    for (pileIndex in nextTableau.indices) {
      val pile = nextTableau[pileIndex]
      if (pile.size < 13) continue
      val candidate = pile.takeLast(13)
      if (!isCompleteRun(candidate)) continue

      nextCompleted.add(candidate.first().card.suit)
      repeat(13) { pile.removeLast() }
      flipExposedTop(pile)
      changed = true
    }
  } while (changed)

  return copy(
    tableau = nextTableau.map { it.toList() },
    completedRuns = nextCompleted,
    selection = null,
  )
}

private fun isCompleteRun(cards: List<SpiderTableauCard>): Boolean {
  if (cards.size != 13 || cards.any { !it.faceUp }) return false
  val suit = cards.first().card.suit
  if (cards.first().card.rank != 13 || cards.last().card.rank != 1) return false
  return cards.all { it.card.suit == suit } && cards.zipWithNext().all { (lower, upper) ->
    lower.card.rank == upper.card.rank + 1
  }
}

private fun flipExposedTop(pile: MutableList<SpiderTableauCard>) {
  val topIndex = pile.lastIndex
  if (topIndex >= 0 && !pile[topIndex].faceUp) {
    pile[topIndex] = pile[topIndex].copy(faceUp = true)
  }
}

private fun SpiderDifficulty.suits(): List<CardSuit> = when (this) {
  SpiderDifficulty.ONE_SUIT -> listOf(CardSuit.SPADES)
  SpiderDifficulty.TWO_SUITS -> listOf(CardSuit.SPADES, CardSuit.HEARTS)
  SpiderDifficulty.FOUR_SUITS -> CardSuit.entries
}
