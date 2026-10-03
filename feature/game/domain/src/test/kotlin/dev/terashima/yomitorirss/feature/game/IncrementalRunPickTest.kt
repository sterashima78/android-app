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

  @Test
  fun lowPressureRewardsSpendingDownCurrentEnergy() {
    val state = IncrementalGame.newGame().copy(
      energy = 10.0,
      runEnergy = 100.0,
      generatorAmounts = listOf(1.0, 0.0, 0.0, 0.0),
      runPicks = listOf(IncrementalRunPick.LOW_PRESSURE),
    )

    assertEquals(3.0, IncrementalGame.productionPerSecond(state), 0.0001)
  }

  @Test
  fun cascadeResonanceSpeedsCascadeButReducesEnergyOutput() {
    val state = IncrementalGame.newGame().copy(
      generatorAmounts = listOf(0.0, 1.0, 0.0, 0.0),
      runPicks = listOf(IncrementalRunPick.CASCADE_RESONANCE),
    )

    val next = IncrementalGame.tick(state, elapsedSeconds = 1.0)

    assertEquals(0.875, next.generatorAmounts[IncrementalGeneratorType.SPARK.ordinal], 0.0001)
    assertEquals(0.328125, next.energy, 0.000001)
  }

  @Test
  fun overclockShortensOverdriveAndRaisesItsMultiplier() {
    val state = IncrementalGame.newGame().copy(
      generatorAmounts = listOf(1.0, 0.0, 0.0, 0.0),
      overdriveRemainingSeconds = 1.0,
      runPicks = listOf(IncrementalRunPick.OVERCLOCK),
    )

    assertEquals(3.3, IncrementalGame.overdriveDurationSeconds(state), 0.0001)
    assertEquals(35.0, IncrementalGame.overdriveProductionMultiplier(state), 0.0001)
    assertEquals(35.0, IncrementalGame.productionPerSecond(state), 0.0001)
  }

  @Test
  fun feedbackLoopExtendsActiveOverdriveAtTapRewardCost() {
    val state = IncrementalGame.newGame().copy(
      overdriveRemainingSeconds = 1.0,
      runPicks = listOf(IncrementalRunPick.FEEDBACK_LOOP),
    )

    val result = IncrementalGame.tap(state)

    assertEquals(0.5, result.gainedEnergy, 0.0001)
    assertEquals(1.15, result.state.overdriveRemainingSeconds, 0.0001)
  }

}
