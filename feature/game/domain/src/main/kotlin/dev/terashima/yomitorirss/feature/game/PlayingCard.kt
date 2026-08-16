package dev.terashima.yomitorirss.feature.game

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
