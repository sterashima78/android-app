package dev.terashima.yomitorirss.feature.game

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class KlondikeViewModel : ViewModel() {
  private val gameFactory = KlondikeGameFactory()
  private val _state = MutableStateFlow(gameFactory.create())
  val state: StateFlow<KlondikeGameState> = _state.asStateFlow()

  fun newGame() {
    _state.value = gameFactory.create()
  }

  fun drawStock() {
    _state.value = _state.value.drawStock()
  }

  fun selectWaste() {
    _state.value = _state.value.selectWaste()
  }

  fun selectFoundation(suit: CardSuit) {
    _state.value = _state.value.selectFoundation(suit)
  }

  fun selectTableau(pileIndex: Int, cardIndex: Int) {
    _state.value = _state.value.selectTableau(pileIndex, cardIndex)
  }

  fun flipTableauTop(pileIndex: Int) {
    _state.value = _state.value.flipTableauTop(pileIndex)
  }

  fun moveSelectedToTableau(pileIndex: Int) {
    _state.value = _state.value.moveSelectedToTableau(pileIndex)
  }

  fun moveSelectedToFoundation(suit: CardSuit) {
    _state.value = _state.value.moveSelectedToFoundation(suit)
  }
}
