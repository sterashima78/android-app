package dev.terashima.yomitorirss.feature.game

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MinesweeperViewModel : ViewModel() {
  private val game = MinesweeperGame()
  private val _state = MutableStateFlow(game.newGame())
  val state: StateFlow<MinesweeperState> = _state.asStateFlow()

  fun newGame() {
    _state.value = game.newGame()
  }

  fun reveal(index: Int) {
    _state.value = game.reveal(_state.value, index)
  }

  fun toggleFlag(index: Int) {
    _state.value = game.toggleFlag(_state.value, index)
  }
}
