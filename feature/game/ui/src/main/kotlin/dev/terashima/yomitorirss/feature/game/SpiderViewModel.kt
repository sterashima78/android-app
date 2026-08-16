package dev.terashima.yomitorirss.feature.game

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SpiderViewModel : ViewModel() {
  private val factory = SpiderGameFactory()
  private val _state = MutableStateFlow(factory.create())
  val state: StateFlow<SpiderGameState> = _state.asStateFlow()

  fun newGame(difficulty: SpiderDifficulty = _state.value.difficulty) {
    _state.value = factory.create(difficulty)
  }

  fun selectTableau(pileIndex: Int, cardIndex: Int) {
    _state.value = _state.value.selectTableau(pileIndex, cardIndex)
  }

  fun moveSelectedToTableau(pileIndex: Int) {
    _state.value = _state.value.moveSelectedToTableau(pileIndex)
  }

  fun dealStock() {
    _state.value = _state.value.dealStock()
  }
}
