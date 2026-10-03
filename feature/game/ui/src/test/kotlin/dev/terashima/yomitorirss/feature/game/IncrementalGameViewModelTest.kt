package dev.terashima.yomitorirss.feature.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IncrementalGameViewModelTest {
  @Test
  fun runPickOfferPausesProgressUntilAChoiceIsMade() {
    val viewModel = IncrementalGameViewModel()

    repeat(500) {
      if (viewModel.state.value.pendingRunPicks.isEmpty()) {
        viewModel.tap()
      }
    }

    val offered = viewModel.state.value
    assertEquals(IncrementalGame.RUN_PICK_OPTION_COUNT, offered.pendingRunPicks.size)

    viewModel.tick(0.25)
    assertEquals(offered.game, viewModel.state.value.game)

    val selected = offered.pendingRunPicks.first()
    viewModel.selectRunPick(selected)

    assertEquals(listOf(selected), viewModel.state.value.game.runPicks)
    assertTrue(viewModel.state.value.pendingRunPicks.isEmpty())
  }
}
