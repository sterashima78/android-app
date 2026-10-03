package dev.terashima.yomitorirss.feature.game

import androidx.lifecycle.ViewModel
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class IncrementalGameUiState(
  val game: IncrementalGameState = IncrementalGame.newGame(),
  val purchaseAmount: IncrementalPurchaseAmount = IncrementalPurchaseAmount.MAX,
  val lastTapReward: Double = 0.0,
  val lastTapWasJackpot: Boolean = false,
  val tapEventId: Long = 0L,
)

class IncrementalGameViewModel : ViewModel() {
  private val _state = MutableStateFlow(IncrementalGameUiState())
  val state: StateFlow<IncrementalGameUiState> = _state.asStateFlow()

  fun tick(elapsedSeconds: Double) {
    val current = _state.value
    _state.value = current.copy(
      game = IncrementalGame.tick(
        state = current.game,
        elapsedSeconds = elapsedSeconds.coerceIn(0.0, 0.25),
      ),
    )
  }

  fun tap() {
    val current = _state.value
    val result = IncrementalGame.tap(
      state = current.game,
      jackpot = Random.nextDouble() < JACKPOT_CHANCE,
    )
    _state.value = current.copy(
      game = result.state,
      lastTapReward = result.gainedEnergy,
      lastTapWasJackpot = result.jackpot,
      tapEventId = current.tapEventId + 1,
    )
  }

  fun setPurchaseAmount(amount: IncrementalPurchaseAmount) {
    _state.value = _state.value.copy(purchaseAmount = amount)
  }

  fun purchase(type: IncrementalGeneratorType) {
    val current = _state.value
    _state.value = current.copy(
      game = IncrementalGame.purchase(
        state = current.game,
        type = type,
        amount = current.purchaseAmount,
      ),
    )
  }

  fun prestige() {
    val current = _state.value
    _state.value = current.copy(
      game = IncrementalGame.prestige(current.game),
      lastTapReward = 0.0,
      lastTapWasJackpot = false,
    )
  }

  fun purchasePrestigeUpgrade(upgrade: IncrementalPrestigeUpgrade) {
    val current = _state.value
    _state.value = current.copy(
      game = IncrementalGame.purchasePrestigeUpgrade(current.game, upgrade),
    )
  }

  private companion object {
    const val JACKPOT_CHANCE = 0.01
  }
}
