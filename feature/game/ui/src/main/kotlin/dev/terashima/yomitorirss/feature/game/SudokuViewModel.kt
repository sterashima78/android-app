package dev.terashima.yomitorirss.feature.game

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SudokuViewModel : ViewModel() {
  private val puzzleFactory = SudokuPuzzleFactory()
  private val _state = MutableStateFlow(puzzleFactory.create().newGameState())
  val state: StateFlow<SudokuGameState> = _state.asStateFlow()

  fun newGame() {
    _state.value = puzzleFactory.create().newGameState()
  }

  fun selectCell(index: Int) {
    _state.value = _state.value.selectCell(index)
  }

  fun enterNumber(value: Int) {
    _state.value = _state.value.enterNumber(value)
  }

  fun clearSelectedCell() {
    _state.value = _state.value.clearSelectedCell()
  }
}
