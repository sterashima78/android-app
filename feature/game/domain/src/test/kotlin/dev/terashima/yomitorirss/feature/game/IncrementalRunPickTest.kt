package dev.terashima.yomitorirss.feature.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncrementalRunPickTest {
  @Test
  fun pickBecomesDueAtMilestoneAndAdvancesAfterSelection() {
    val due = IncrementalGame.newGame().copy(runEnergy = 100.0)

    assertTrue(IncrementalGame.isRunPickDue(due))

    val selected = IncrementalGame.selectRunPick(due, IncrementalRunPick.RHYTHM_RELAY)

    assertEquals(listOf(IncrementalRunPick.RHYTHM_RELAY), selected.runPicks)
    assertFalse(IncrementalGame.isRunPickDue(selected))
    assertFalse(IncrementalGame.availableRunPicks(selected).contains(IncrementalRunPick.RHYTHM_RELAY))
  }

  @Test
  fun picksResetOnPrestige() {
    val state = IncrementalGame.newGame().copy(
      runEnergy = IncrementalGame.PRESTIGE_THRESHOLD,
      runPicks = listOf(IncrementalRunPick.RHYTHM_RELAY, IncrementalRunPick.LOW_PRESSURE),
    )

    val next = IncrementalGame.prestige(state)

    assertTrue(next.runPicks.isEmpty())
    assertEquals(1, next.prestigeCores)
  }

  @Test
  fun rhythmRelayBoostsEveryTenthTap() {
    val state = IncrementalGame.newGame().copy(
      runPicks = listOf(IncrementalRunPick.RHYTHM_RELAY),
      taps = 9,
    )

    assertEquals(8.0, IncrementalGame.tap(state).gainedEnergy, 0.0001)
  }

  @Test
  fun manualOverrideTradesAutomaticProductionForTapPower() {
    val state = IncrementalGame.newGame().copy(
      generatorAmounts = listOf(100.0, 0.0, 0.0, 0.0),
      runPicks = listOf(IncrementalRunPick.MANUAL_OVERRIDE),
    )

    assertEquals(60.0, IncrementalGame.productionPerSecond(state), 0.0001)
    assertEquals(19.2, IncrementalGame.tap(state).gainedEnergy, 0.0001)
  }
}
