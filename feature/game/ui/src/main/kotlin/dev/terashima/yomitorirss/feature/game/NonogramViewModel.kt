package dev.terashima.yomitorirss.feature.game

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NonogramViewModel : ViewModel() {
  private val puzzleFactory = NonogramPuzzleFactory()
  private val _state = MutableStateFlow(puzzleFactory.create().newGameState())
  val state: StateFlow<NonogramGameState> = _state.asStateFlow()

  fun newGame() {
    _state.value = puzzleFactory.create().newGameState()
  }

  fun fill(index: Int) {
    _state.value = _state.value.fill(index)
  }

  fun mark(index: Int) {
    _state.value = _state.value.mark(index)
  }
}
