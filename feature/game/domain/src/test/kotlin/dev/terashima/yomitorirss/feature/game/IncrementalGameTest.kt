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
    assertEquals(5.0, result.state.overdriveCharge, 0.0)
    assertFalse(result.overdriveStarted)
  }

  @Test
  fun twentiethTapStartsOverdrive() {
    var state = IncrementalGame.newGame()

    repeat(19) {
      state = IncrementalGame.tap(state).state
    }

    val result = IncrementalGame.tap(state)

    assertTrue(result.overdriveStarted)
    assertEquals(0.0, result.state.overdriveCharge, 0.0)
    assertEquals(IncrementalGame.OVERDRIVE_DURATION_SECONDS, result.state.overdriveRemainingSeconds, 0.0)
  }

  @Test
  fun jackpotMultipliesTapReward() {
    val result = IncrementalGame.tap(
      state = IncrementalGame.newGame(),
      jackpot = true,
    )

    assertEquals(IncrementalGame.JACKPOT_MULTIPLIER, result.gainedEnergy, 0.0)
    assertTrue(result.jackpot)
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
  fun reactorProducesSparksWhichProduceEnergy() {
    val state = IncrementalGame.newGame().copy(
      generatorAmounts = listOf(0.0, 1.0, 0.0, 0.0),
    )

    val next = IncrementalGame.tick(state, elapsedSeconds = 1.0)

    assertEquals(1.0, next.generatorAmounts[IncrementalGeneratorType.SPARK.ordinal], 0.0001)
    assertEquals(0.5, next.energy, 0.0001)
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
  fun overdriveMultipliesAutomaticProduction() {
    val state = IncrementalGame.newGame().copy(
      generatorAmounts = listOf(1.0, 0.0, 0.0, 0.0),
      overdriveRemainingSeconds = 1.0,
    )

    val next = IncrementalGame.tick(state, elapsedSeconds = 1.0)

    assertEquals(1_000.0, next.energy, 0.0001)
    assertEquals(0.0, next.overdriveRemainingSeconds, 0.0001)
  }

  @Test
  fun prestigeResetsRunAndKeepsPermanentPoints() {
    val state = IncrementalGame.newGame(prestigePoints = 2).copy(
      energy = IncrementalGame.PRESTIGE_THRESHOLD,
      runEnergy = IncrementalGame.PRESTIGE_THRESHOLD,
      generatorAmounts = listOf(100.0, 10.0, 1.0, 0.0),
      generatorPurchases = listOf(100, 10, 1, 0),
    )

    assertEquals(1, IncrementalGame.prestigeReward(state))

    val next = IncrementalGame.prestige(state)

    assertEquals(3, next.prestigePoints)
    assertEquals(0.0, next.energy, 0.0)
    assertEquals(0.0, next.runEnergy, 0.0)
    assertTrue(next.generatorAmounts.all { it == 0.0 })
    assertTrue(next.generatorPurchases.all { it == 0 })
  }
}
