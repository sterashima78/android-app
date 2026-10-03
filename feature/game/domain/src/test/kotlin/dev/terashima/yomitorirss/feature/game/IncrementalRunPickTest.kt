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

    val selected = IncrementalGame.selectRunPick(due, IncrementalRunPick.MANUAL_OVERRIDE)

    assertEquals(listOf(IncrementalRunPick.MANUAL_OVERRIDE), selected.runPicks)
    assertFalse(IncrementalGame.isRunPickDue(selected))
    assertFalse(IncrementalGame.availableRunPicks(selected).contains(IncrementalRunPick.MANUAL_OVERRIDE))
  }

  @Test
  fun unitSpecificPicksUnlockAsTheRunReachesTheirTier() {
    val sparkTier = IncrementalGame.newGame().copy(runEnergy = 100.0)
    val reactorTier = sparkTier.copy(runEnergy = 10_000.0)
    val forgeTier = sparkTier.copy(runEnergy = 2_500_000.0)
    val singularityTier = sparkTier.copy(runEnergy = 1_200_000_000.0)

    assertTrue(IncrementalRunPick.SPARK_DISCHARGE in IncrementalGame.availableRunPicks(sparkTier))
    assertFalse(IncrementalRunPick.REACTOR_SURGE in IncrementalGame.availableRunPicks(sparkTier))
    assertTrue(IncrementalRunPick.REACTOR_SURGE in IncrementalGame.availableRunPicks(reactorTier))
    assertTrue(IncrementalRunPick.FORGE_COMPRESSION in IncrementalGame.availableRunPicks(forgeTier))
    assertTrue(IncrementalRunPick.SINGULARITY_COLLAPSE in IncrementalGame.availableRunPicks(singularityTier))
  }

  @Test
  fun picksResetOnPrestige() {
    val state = IncrementalGame.newGame().copy(
      runEnergy = IncrementalGame.PRESTIGE_THRESHOLD,
      runPicks = listOf(IncrementalRunPick.MANUAL_OVERRIDE, IncrementalRunPick.LOW_PRESSURE),
    )

    val next = IncrementalGame.prestige(state)

    assertTrue(next.runPicks.isEmpty())
    assertEquals(1, next.prestigeCores)
  }

  @Test
  fun sparkDischargeAddsSeveralSecondsOfSparkProductionEveryTenthTap() {
    val state = IncrementalGame.newGame().copy(
      generatorAmounts = listOf(100.0, 0.0, 0.0, 0.0),
      runPicks = listOf(IncrementalRunPick.SPARK_DISCHARGE),
      taps = 9,
    )

    assertEquals(333.0, IncrementalGame.tap(state).gainedEnergy, 0.0001)
  }

  @Test
  fun manualOverrideTradesAutomaticProductionForTapPower() {
    val state = IncrementalGame.newGame().copy(
      generatorAmounts = listOf(100.0, 0.0, 0.0, 0.0),
      runPicks = listOf(IncrementalRunPick.MANUAL_OVERRIDE),
    )

    assertEquals(60.0, IncrementalGame.productionPerSecond(state), 0.0001)
    assertEquals(79.2, IncrementalGame.tap(state).gainedEnergy, 0.0001)
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
  fun reactorSurgeTradesNormalOutputForExtremeOverdriveOutput() {
    val normal = IncrementalGame.newGame().copy(
      generatorAmounts = listOf(0.0, 1.0, 0.0, 0.0),
      runPicks = listOf(IncrementalRunPick.REACTOR_SURGE),
    )
    val overdrive = normal.copy(overdriveRemainingSeconds = 1.0)

    assertEquals(35.0, IncrementalGame.productionPerSecond(normal), 0.0001)
    assertEquals(4_200.0, IncrementalGame.productionPerSecond(overdrive), 0.0001)
  }

  @Test
  fun forgeCompressionMakesStarForgeMilestonesMoreFrequent() {
    val normal = IncrementalGame.newGame().copy(
      generatorAmounts = listOf(0.0, 0.0, 15.0, 0.0),
      generatorPurchases = listOf(0, 0, 15, 0),
    )
    val compressed = normal.copy(runPicks = listOf(IncrementalRunPick.FORGE_COMPRESSION))

    assertEquals(75_000.0, IncrementalGame.productionPerSecond(normal), 0.0001)
    assertEquals(15, IncrementalGame.milestoneInterval(compressed, IncrementalGeneratorType.STAR_FORGE))
    assertEquals(225_000.0, IncrementalGame.productionPerSecond(compressed), 0.0001)
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

  @Test
  fun singularityCollapseConcentratesProductionIntoSingularities() {
    val state = IncrementalGame.newGame().copy(
      generatorAmounts = listOf(1.0, 0.0, 0.0, 1.0),
      runPicks = listOf(IncrementalRunPick.SINGULARITY_COLLAPSE),
    )

    assertEquals(
      7_000_000.0,
      IncrementalGame.generatorProductionPerSecond(state, IncrementalGeneratorType.SINGULARITY),
      0.001,
    )
    assertEquals(
      0.5,
      IncrementalGame.generatorProductionPerSecond(state, IncrementalGeneratorType.SPARK),
      0.0001,
    )
  }
}
