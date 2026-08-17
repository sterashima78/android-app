package dev.terashima.yomitorirss.feature.game

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class Game2048ViewModel : ViewModel() {
  private val game = Game2048()
  private val _state = MutableStateFlow(game.newGame())
  val state: StateFlow<Game2048State> = _state.asStateFlow()

  fun newGame() {
    _state.value = game.newGame()
  }

  fun move(direction: Game2048Direction) {
    _state.value = game.move(_state.value, direction)
  }
}
