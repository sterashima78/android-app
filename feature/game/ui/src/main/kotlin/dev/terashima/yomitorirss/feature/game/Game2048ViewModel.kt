package dev.terashima.yomitorirss.feature.game

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Game2048UiState(
  val game: Game2048State,
  val transition: Game2048MoveResult? = null,
  val transitionId: Long = 0L,
)

class Game2048ViewModel : ViewModel() {
  private val game = Game2048()
  private val _state = MutableStateFlow(Game2048UiState(game = game.newGame()))
  val state: StateFlow<Game2048UiState> = _state.asStateFlow()

  fun newGame() {
    val current = _state.value
    _state.value = Game2048UiState(
      game = game.newGame(),
      transitionId = current.transitionId + 1,
    )
  }

  fun move(direction: Game2048Direction) {
    val current = _state.value
    if (current.transition != null) return

    val result = game.moveWithTransition(current.game, direction)
    _state.value = Game2048UiState(
      game = result.state,
      transition = result,
      transitionId = current.transitionId + 1,
    )
  }

  fun completeTransition(transitionId: Long) {
    val current = _state.value
    if (current.transitionId != transitionId) return
    _state.value = current.copy(transition = null)
  }
}
