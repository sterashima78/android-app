package dev.terashima.yomitorirss.feature.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncrementalGameTest {
  @Test
  fun tapAddsEnergyAndChargesOverdrive() {
    val result = IncrementalGame.tap(IncrementalGame.newGame())

    assertEquals(1.0, result.gainedEnergy, 0.0)
    assertEquals(1.0, result.state.energy, 0.0)
    assertEquals(4.0, result.state.overdriveCharge, 0.0)
    assertFalse(result.overdriveStarted)
  }

  @Test
  fun twentyFifthTapStartsOverdrive() {
    var state = IncrementalGame.newGame()

    repeat(24) {
      state = IncrementalGame.tap(state).state
    }

    val result = IncrementalGame.tap(state)

    assertTrue(result.overdriveStarted)
    assertEquals(0.0, result.state.overdriveCharge, 0.0)
    assertEquals(IncrementalGame.BASE_OVERDRIVE_DURATION_SECONDS, result.state.overdriveRemainingSeconds, 0.0)
  }

  @Test
  fun jackpotUsesReducedMultiplier() {
    val result = IncrementalGame.tap(
      state = IncrementalGame.newGame(),
      jackpot = true,
    )

    assertEquals(IncrementalGame.JACKPOT_MULTIPLIER, result.gainedEnergy, 0.0)
    assertEquals(50.0, result.gainedEnergy, 0.0)
  }

  @Test
  fun sparkProducesEnergyOverTime() {
    val state = IncrementalGame.newGame().copy(
      generatorAmounts = listOf(10.0, 0.0, 0.0, 0.0),
    )

    val next = IncrementalGame.tick(state, elapsedSeconds = 2.0)

    assertEquals(20.0, next.energy, 0.0001)
    assertEquals(20.0, next.runEnergy, 0.0001)
  }

  @Test
  fun reactorProducesSparksGradually() {
    val state = IncrementalGame.newGame().copy(
      generatorAmounts = listOf(0.0, 1.0, 0.0, 0.0),
    )

    val next = IncrementalGame.tick(state, elapsedSeconds = 1.0)

    assertEquals(0.5, next.generatorAmounts[IncrementalGeneratorType.SPARK.ordinal], 0.0001)
    assertEquals(0.25, next.energy, 0.0001)
  }

  @Test
  fun cascadeProductionDoesNotDependOnTickSize() {
    val initial = IncrementalGame.newGame().copy(
      generatorAmounts = listOf(0.0, 1.0, 1.0, 1.0),
    )

    val singleTick = IncrementalGame.tick(initial, elapsedSeconds = 1.0)
    val splitTicks = generateSequence(initial) { state ->
      IncrementalGame.tick(state, elapsedSeconds = 0.1)
    }.drop(10).first()

    singleTick.generatorAmounts.zip(splitTicks.generatorAmounts).forEach { (single, split) ->
      assertEquals(single, split, 0.000001)
    }
    assertEquals(singleTick.energy, splitTicks.energy, 0.000001)
  }

  @Test
  fun purchaseConsumesEnergyAndAddsGenerator() {
    val state = IncrementalGame.newGame().copy(energy = 100.0)

    val next = IncrementalGame.purchase(
      state = state,
      type = IncrementalGeneratorType.SPARK,
      amount = IncrementalPurchaseAmount.ONE,
    )

    assertEquals(90.0, next.energy, 0.0001)
    assertEquals(1.0, next.generatorAmounts[IncrementalGeneratorType.SPARK.ordinal], 0.0001)
    assertEquals(1, next.generatorPurchases[IncrementalGeneratorType.SPARK.ordinal])
  }

  @Test
  fun milestoneDoublesProductionInsteadOfMultiplyingByTen() {
    val state = IncrementalGame.newGame().copy(
      generatorAmounts = listOf(25.0, 0.0, 0.0, 0.0),
      generatorPurchases = listOf(25, 0, 0, 0),
    )

    assertEquals(50.0, IncrementalGame.productionPerSecond(state), 0.0001)
  }

  @Test
  fun overdriveMultipliesAutomaticProductionByTwenty() {
    val state = IncrementalGame.newGame().copy(
      generatorAmounts = listOf(1.0, 0.0, 0.0, 0.0),
      overdriveRemainingSeconds = 1.0,
    )

    val next = IncrementalGame.tick(state, elapsedSeconds = 1.0)

    assertEquals(20.0, next.energy, 0.0001)
    assertEquals(0.0, next.overdriveRemainingSeconds, 0.0001)
  }

  @Test
  fun prestigeRewardHasDiminishingSquareRootScaling() {
    val oneCore = IncrementalGame.newGame().copy(runEnergy = 3_000_000.0)
    val twoCores = IncrementalGame.newGame().copy(runEnergy = 12_000_000.0)
    val threeCores = IncrementalGame.newGame().copy(runEnergy = 27_000_000.0)

    assertEquals(1, IncrementalGame.prestigeReward(oneCore))
    assertEquals(2, IncrementalGame.prestigeReward(twoCores))
    assertEquals(3, IncrementalGame.prestigeReward(threeCores))
  }

  @Test
  fun prestigeGivesSpendableCoresWithoutAutomaticMultiplier() {
    val state = IncrementalGame.newGame().copy(
      energy = IncrementalGame.PRESTIGE_THRESHOLD,
      runEnergy = IncrementalGame.PRESTIGE_THRESHOLD,
      generatorAmounts = listOf(100.0, 10.0, 1.0, 0.0),
      generatorPurchases = listOf(100, 10, 1, 0),
    )

    val next = IncrementalGame.prestige(state)

    assertEquals(1, next.prestigeCores)
    assertEquals(0.0, next.energy, 0.0)
    assertEquals(1.0, IncrementalGame.outputMultiplier(next), 0.0)
    assertTrue(next.generatorAmounts.all { it == 0.0 })
  }

  @Test
  fun prestigeCoreCanBeSpentOnDifferentUpgradeBranches() {
    val state = IncrementalGame.newGame(prestigeCores = 1)

    val output = IncrementalGame.purchasePrestigeUpgrade(
      state,
      IncrementalPrestigeUpgrade.OUTPUT_AMPLIFIER,
    )
    val tap = IncrementalGame.purchasePrestigeUpgrade(
      state,
      IncrementalPrestigeUpgrade.TAP_RESONANCE,
    )

    assertEquals(0, output.prestigeCores)
    assertEquals(1, IncrementalGame.prestigeUpgradeLevel(output, IncrementalPrestigeUpgrade.OUTPUT_AMPLIFIER))
    assertEquals(1.35, IncrementalGame.outputMultiplier(output), 0.0001)

    assertEquals(0, tap.prestigeCores)
    assertEquals(1, IncrementalGame.prestigeUpgradeLevel(tap, IncrementalPrestigeUpgrade.TAP_RESONANCE))
    assertEquals(1.75, IncrementalGame.tapMultiplier(tap), 0.0001)
  }

  @Test
  fun starterSparkUnlockRequiresThreePermanentLevels() {
    val locked = IncrementalGame.newGame(prestigeCores = 10)
    assertFalse(
      IncrementalGame.isPrestigeUpgradeUnlocked(
        locked,
        IncrementalPrestigeUpgrade.STARTER_SPARK,
      ),
    )

    val unlocked = locked.copy(
      prestigeUpgradeLevels = listOf(1, 1, 1, 0, 0, 0),
    )
    assertTrue(
      IncrementalGame.isPrestigeUpgradeUnlocked(
        unlocked,
        IncrementalPrestigeUpgrade.STARTER_SPARK,
      ),
    )
  }

  @Test
  fun starterSparkAppliesImmediatelyAndToFutureRuns() {
    val state = IncrementalGame.newGame(prestigeCores = 3).copy(
      prestigeUpgradeLevels = listOf(1, 1, 1, 0, 0, 0),
    )

    val upgraded = IncrementalGame.purchasePrestigeUpgrade(
      state,
      IncrementalPrestigeUpgrade.STARTER_SPARK,
    )
    val nextRun = IncrementalGame.newGame(
      prestigeCores = upgraded.prestigeCores,
      prestigeUpgradeLevels = upgraded.prestigeUpgradeLevels,
    )

    assertEquals(1.0, upgraded.generatorAmounts[IncrementalGeneratorType.SPARK.ordinal], 0.0)
    assertEquals(1.0, nextRun.generatorAmounts[IncrementalGeneratorType.SPARK.ordinal], 0.0)
  }

  @Test
  fun compactMilestonesReducePurchaseIntervalToTwenty() {
    val state = IncrementalGame.newGame(prestigeCores = 5).copy(
      prestigeUpgradeLevels = listOf(2, 2, 1, 1, 0, 0),
    )

    val upgraded = IncrementalGame.purchasePrestigeUpgrade(
      state,
      IncrementalPrestigeUpgrade.COMPACT_MILESTONES,
    )

    assertEquals(20, IncrementalGame.milestoneInterval(upgraded))
  }
}
