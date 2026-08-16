package dev.terashima.yomitorirss.feature.game

import kotlin.random.Random

enum class CardSuit(val isRed: Boolean) {
  CLUBS(false),
  DIAMONDS(true),
  HEARTS(true),
  SPADES(false),
}

data class PlayingCard(
  val suit: CardSuit,
  val rank: Int,
) {
  init {
    require(rank in 1..13) { "カードのランクは1から13である必要があります" }
  }
}

data class TableauCard(
  val card: PlayingCard,
  val faceUp: Boolean,
)

sealed interface KlondikeSelection {
  data object Waste : KlondikeSelection
  data class Foundation(val suit: CardSuit) : KlondikeSelection
  data class Tableau(val pileIndex: Int, val cardIndex: Int) : KlondikeSelection
}

data class KlondikeGameState(
  val stock: List<PlayingCard>,
  val waste: List<PlayingCard>,
  val foundations: Map<CardSuit, List<PlayingCard>>,
  val tableau: List<List<TableauCard>>,
  val selection: KlondikeSelection?,
  val moves: Int,
) {
  init {
    require(tableau.size == 7) { "クロンダイクの場札は7列必要です" }
  }

  val isWon: Boolean
    get() = foundations.values.sumOf { it.size } == 52
}

class KlondikeGameFactory(
  private val random: Random = Random.Default,
) {
  fun create(): KlondikeGameState {
    val deck = CardSuit.entries
      .flatMap { suit -> (1..13).map { rank -> PlayingCard(suit, rank) } }
      .shuffled(random)
    var cursor = 0
    val tableau = (0 until 7).map { pileIndex ->
      List(pileIndex + 1) { cardIndex ->
        TableauCard(
          card = deck[cursor++],
          faceUp = cardIndex == pileIndex,
        )
      }
    }

    return KlondikeGameState(
      stock = deck.drop(cursor),
      waste = emptyList(),
      foundations = CardSuit.entries.associateWith { emptyList() },
      tableau = tableau,
      selection = null,
      moves = 0,
    )
  }
}

fun KlondikeGameState.drawStock(): KlondikeGameState = when {
  stock.isNotEmpty() -> copy(
    stock = stock.dropLast(1),
    waste = waste + stock.last(),
    selection = null,
    moves = moves + 1,
  )

  waste.isNotEmpty() -> copy(
    stock = waste.asReversed(),
    waste = emptyList(),
    selection = null,
    moves = moves + 1,
  )

  else -> this
}

fun KlondikeGameState.selectWaste(): KlondikeGameState {
  if (waste.isEmpty() || isWon) return this
  return copy(selection = if (selection == KlondikeSelection.Waste) null else KlondikeSelection.Waste)
}

fun KlondikeGameState.selectFoundation(suit: CardSuit): KlondikeGameState {
  if (foundations[suit].orEmpty().isEmpty() || isWon) return this
  val next = KlondikeSelection.Foundation(suit)
  return copy(selection = if (selection == next) null else next)
}

fun KlondikeGameState.selectTableau(pileIndex: Int, cardIndex: Int): KlondikeGameState {
  val pile = tableau.getOrNull(pileIndex) ?: return this
  val card = pile.getOrNull(cardIndex) ?: return this
  if (!card.faceUp || isWon) return this
  val next = KlondikeSelection.Tableau(pileIndex, cardIndex)
  return copy(selection = if (selection == next) null else next)
}

fun KlondikeGameState.flipTableauTop(pileIndex: Int): KlondikeGameState {
  val pile = tableau.getOrNull(pileIndex) ?: return this
  val top = pile.lastOrNull() ?: return this
  if (top.faceUp || isWon) return this
  val nextTableau = tableau.toMutableList()
  nextTableau[pileIndex] = pile.dropLast(1) + top.copy(faceUp = true)
  return copy(tableau = nextTableau, selection = null, moves = moves + 1)
}

fun KlondikeGameState.moveSelectedToTableau(targetPileIndex: Int): KlondikeGameState {
  val selected = selection ?: return this
  if (selected is KlondikeSelection.Tableau && selected.pileIndex == targetPileIndex) {
    return copy(selection = null)
  }
  val moving = selectedCards(selected) ?: return this
  if (!isValidTableauRun(moving)) return this
  val targetPile = tableau.getOrNull(targetPileIndex) ?: return this
  if (!canPlaceOnTableau(moving.first(), targetPile.lastOrNull())) return this

  val mutableTableau = tableau.map { it.toMutableList() }.toMutableList()
  val mutableWaste = waste.toMutableList()
  val mutableFoundations = foundations.mapValues { it.value.toMutableList() }.toMutableMap()
  removeSelection(selected, mutableTableau, mutableWaste, mutableFoundations)
  mutableTableau[targetPileIndex].addAll(moving.map { TableauCard(it, faceUp = true) })

  return copy(
    tableau = mutableTableau.map { it.toList() },
    waste = mutableWaste.toList(),
    foundations = mutableFoundations.mapValues { it.value.toList() },
    selection = null,
    moves = moves + 1,
  )
}

fun KlondikeGameState.moveSelectedToFoundation(targetSuit: CardSuit): KlondikeGameState {
  val selected = selection ?: return this
  if (selected is KlondikeSelection.Foundation && selected.suit == targetSuit) {
    return copy(selection = null)
  }
  val moving = selectedCards(selected) ?: return this
  if (moving.size != 1) return this
  val card = moving.single()
  if (card.suit != targetSuit) return this
  val target = foundations[targetSuit].orEmpty()
  if (card.rank != target.size + 1) return this

  val mutableTableau = tableau.map { it.toMutableList() }.toMutableList()
  val mutableWaste = waste.toMutableList()
  val mutableFoundations = foundations.mapValues { it.value.toMutableList() }.toMutableMap()
  removeSelection(selected, mutableTableau, mutableWaste, mutableFoundations)
  mutableFoundations.getValue(targetSuit).add(card)

  return copy(
    tableau = mutableTableau.map { it.toList() },
    waste = mutableWaste.toList(),
    foundations = mutableFoundations.mapValues { it.value.toList() },
    selection = null,
    moves = moves + 1,
  )
}

private fun KlondikeGameState.selectedCards(selection: KlondikeSelection): List<PlayingCard>? {
  return when (selection) {
    KlondikeSelection.Waste -> waste.lastOrNull()?.let(::listOf)
    is KlondikeSelection.Foundation -> foundations[selection.suit]?.lastOrNull()?.let(::listOf)
    is KlondikeSelection.Tableau -> {
      val pile = tableau.getOrNull(selection.pileIndex) ?: return null
      if (selection.cardIndex !in pile.indices) return null
      pile.drop(selection.cardIndex)
        .takeIf { cards -> cards.isNotEmpty() && cards.all { it.faceUp } }
        ?.map { it.card }
    }
  }
}

private fun isValidTableauRun(cards: List<PlayingCard>): Boolean =
  cards.zipWithNext().all { (upper, lower) ->
    upper.rank == lower.rank + 1 && upper.suit.isRed != lower.suit.isRed
  }

private fun canPlaceOnTableau(card: PlayingCard, target: TableauCard?): Boolean = when {
  target == null -> card.rank == 13
  !target.faceUp -> false
  else -> target.card.rank == card.rank + 1 && target.card.suit.isRed != card.suit.isRed
}

private fun removeSelection(
  selection: KlondikeSelection,
  tableau: MutableList<MutableList<TableauCard>>,
  waste: MutableList<PlayingCard>,
  foundations: MutableMap<CardSuit, MutableList<PlayingCard>>,
) {
  when (selection) {
    KlondikeSelection.Waste -> waste.removeLast()
    is KlondikeSelection.Foundation -> foundations.getValue(selection.suit).removeLast()
    is KlondikeSelection.Tableau -> {
      val source = tableau[selection.pileIndex]
      source.subList(selection.cardIndex, source.size).clear()
      val exposedIndex = source.lastIndex
      if (exposedIndex >= 0 && !source[exposedIndex].faceUp) {
        source[exposedIndex] = source[exposedIndex].copy(faceUp = true)
      }
    }
  }
}
