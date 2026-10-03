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
  fun everyGeneratorProducesEnergyDirectly() {
    val expected = mapOf(
      IncrementalGeneratorType.SPARK to 1.0,
      IncrementalGeneratorType.REACTOR to 50.0,
      IncrementalGeneratorType.STAR_FORGE to 5_000.0,
      IncrementalGeneratorType.SINGULARITY to 1_000_000.0,
    )

    expected.forEach { (type, production) ->
      val amounts = MutableList(IncrementalGeneratorType.entries.size) { 0.0 }
      amounts[type.ordinal] = 1.0
      val state = IncrementalGame.newGame().copy(generatorAmounts = amounts)

      assertEquals(production, IncrementalGame.generatorProductionPerSecond(state, type), 0.0001)
      assertEquals(production, IncrementalGame.tick(state, elapsedSeconds = 1.0).energy, 0.0001)
    }
  }

  @Test
  fun automaticProductionDoesNotCreateOtherGenerators() {
    val initial = IncrementalGame.newGame().copy(
      generatorAmounts = listOf(1.0, 1.0, 1.0, 1.0),
    )

    val next = IncrementalGame.tick(initial, elapsedSeconds = 10.0)

    assertEquals(initial.generatorAmounts, next.generatorAmounts)
    assertTrue(next.energy > 0.0)
  }

  @Test
  fun sparkProductionAlsoContributesToTapReward() {
    val state = IncrementalGame.newGame().copy(
      generatorAmounts = listOf(100.0, 0.0, 0.0, 0.0),
    )

    assertEquals(33.0, IncrementalGame.tap(state).gainedEnergy, 0.0001)
  }

  @Test
  fun reactorGetsAnExtraMultiplierDuringOverdrive() {
    val state = IncrementalGame.newGame().copy(
      generatorAmounts = listOf(0.0, 1.0, 0.0, 0.0),
      overdriveRemainingSeconds = 1.0,
    )

    assertEquals(1_500.0, IncrementalGame.productionPerSecond(state), 0.0001)
  }

  @Test
  fun starForgeHasStrongerPurchaseMilestones() {
    val state = IncrementalGame.newGame().copy(
      generatorAmounts = listOf(0.0, 0.0, 25.0, 0.0),
      generatorPurchases = listOf(0, 0, 25, 0),
    )

    assertEquals(3.0, IncrementalGame.milestoneProductionMultiplier(state, IncrementalGeneratorType.STAR_FORGE), 0.0001)
    assertEquals(375_000.0, IncrementalGame.productionPerSecond(state), 0.0001)
  }

  @Test
  fun singularityBenefitsFromOwningOtherGeneratorTypes() {
    val state = IncrementalGame.newGame().copy(
      generatorAmounts = listOf(1.0, 1.0, 1.0, 1.0),
    )

    assertEquals(
      2_744_000.0,
      IncrementalGame.generatorProductionPerSecond(state, IncrementalGeneratorType.SINGULARITY),
      0.001,
    )
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
  fun normalMilestoneDoublesProduction() {
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
  fun specializationUpgradeStrengthensGeneratorTraits() {
    val state = IncrementalGame.newGame(prestigeCores = 1)

    val upgraded = IncrementalGame.purchasePrestigeUpgrade(
      state,
      IncrementalPrestigeUpgrade.SPECIALIZATION_TUNING,
    )

    assertEquals(1.1, IncrementalGame.specializationMultiplier(upgraded), 0.0001)
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
