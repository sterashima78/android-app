package dev.terashima.yomitorirss.feature.game

import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

enum class IncrementalGeneratorType(
  val baseCost: Double,
  val costGrowth: Double,
  val cascadePerSecond: Double,
) {
  SPARK(10.0, 1.17, 0.0),
  REACTOR(5_000.0, 1.20, 0.5),
  STAR_FORGE(2_000_000.0, 1.23, 0.2),
  SINGULARITY(1_000_000_000.0, 1.26, 0.08),
}

enum class IncrementalPurchaseAmount {
  ONE,
  TEN,
  MAX,
}

enum class IncrementalRunPick {
  RHYTHM_RELAY,
  MANUAL_OVERRIDE,
  LOW_PRESSURE,
  CASCADE_RESONANCE,
  OVERCLOCK,
  FEEDBACK_LOOP,
}

enum class IncrementalPrestigeUpgrade(
  val baseCost: Int,
  val maxLevel: Int,
  val requiredTotalLevels: Int = 0,
) {
  OUTPUT_AMPLIFIER(1, 5),
  CASCADE_TUNING(1, 5),
  TAP_RESONANCE(1, 5),
  OVERDRIVE_CAPACITOR(1, 5),
  STARTER_SPARK(3, 1, 3),
  COMPACT_MILESTONES(5, 1, 6),
}

data class IncrementalGameState(
  val energy: Double = 0.0,
  val runEnergy: Double = 0.0,
  val generatorAmounts: List<Double> = List(IncrementalGeneratorType.entries.size) { 0.0 },
  val generatorPurchases: List<Int> = List(IncrementalGeneratorType.entries.size) { 0 },
  val overdriveCharge: Double = 0.0,
  val overdriveRemainingSeconds: Double = 0.0,
  val prestigeCores: Int = 0,
  val prestigeUpgradeLevels: List<Int> = List(IncrementalPrestigeUpgrade.entries.size) { 0 },
  val runPicks: List<IncrementalRunPick> = emptyList(),
  val taps: Long = 0,
)

data class IncrementalTapResult(
  val state: IncrementalGameState,
  val gainedEnergy: Double,
  val jackpot: Boolean,
  val overdriveStarted: Boolean,
)

object IncrementalGame {
  const val OVERDRIVE_CHARGE_MAX = 100.0
  const val OVERDRIVE_CHARGE_PER_TAP = 4.0
  const val BASE_OVERDRIVE_DURATION_SECONDS = 6.0
  const val OVERDRIVE_MULTIPLIER = 20.0
  const val JACKPOT_MULTIPLIER = 50.0
  const val PRESTIGE_THRESHOLD = 3_000_000.0
  const val MILESTONE_MULTIPLIER = 2.0
  const val BASE_MILESTONE_INTERVAL = 25
  const val COMPACT_MILESTONE_INTERVAL = 20
  const val TAP_PRODUCTION_FRACTION = 0.08
  const val RUN_PICK_OPTION_COUNT = 3
  val RUN_PICK_THRESHOLDS = listOf(100.0, 5_000.0, 100_000.0, 1_000_000.0)

  private const val RHYTHM_RELAY_INTERVAL = 10L
  private const val RHYTHM_RELAY_MULTIPLIER = 8.0
  private const val MANUAL_OVERRIDE_AUTO_MULTIPLIER = 0.6
  private const val MANUAL_OVERRIDE_TAP_MULTIPLIER = 4.0
  private const val LOW_PRESSURE_THRESHOLD = 0.20
  private const val LOW_PRESSURE_MULTIPLIER = 3.0
  private const val CASCADE_RESONANCE_MULTIPLIER = 1.75
  private const val CASCADE_RESONANCE_OUTPUT_MULTIPLIER = 0.75
  private const val OVERCLOCK_MULTIPLIER = 35.0
  private const val OVERCLOCK_DURATION_MULTIPLIER = 0.55
  private const val FEEDBACK_TAP_MULTIPLIER = 0.5
  private const val FEEDBACK_EXTENSION_SECONDS = 0.15

  fun newGame(
    prestigeCores: Int = 0,
    prestigeUpgradeLevels: List<Int> = List(IncrementalPrestigeUpgrade.entries.size) { 0 },
  ): IncrementalGameState {
    val normalizedLevels = normalizePrestigeLevels(prestigeUpgradeLevels)
    val starterSpark = if (
      normalizedLevels[IncrementalPrestigeUpgrade.STARTER_SPARK.ordinal] > 0
    ) {
      1.0
    } else {
      0.0
    }
    return IncrementalGameState(
      generatorAmounts = listOf(starterSpark, 0.0, 0.0, 0.0),
      prestigeCores = prestigeCores.coerceAtLeast(0),
      prestigeUpgradeLevels = normalizedLevels,
    )
  }

  fun tap(
    state: IncrementalGameState,
    jackpot: Boolean = false,
  ): IncrementalTapResult {
    val overdriveWasActive = state.overdriveRemainingSeconds > 0.0
    val normalProductionPerSecond = productionPerSecond(state.copy(overdriveRemainingSeconds = 0.0))
    val baseTap = maxOf(1.0, normalProductionPerSecond * TAP_PRODUCTION_FRACTION)
    val rhythmMultiplier = if (
      hasRunPick(state, IncrementalRunPick.RHYTHM_RELAY) &&
      (state.taps + 1) % RHYTHM_RELAY_INTERVAL == 0L
    ) {
      RHYTHM_RELAY_MULTIPLIER
    } else {
      1.0
    }
    val manualTapMultiplier = if (hasRunPick(state, IncrementalRunPick.MANUAL_OVERRIDE)) {
      MANUAL_OVERRIDE_TAP_MULTIPLIER
    } else {
      1.0
    }
    val feedbackTapMultiplier = if (
      overdriveWasActive && hasRunPick(state, IncrementalRunPick.FEEDBACK_LOOP)
    ) {
      FEEDBACK_TAP_MULTIPLIER
    } else {
      1.0
    }
    val gainedEnergy = scaledProduct(
      baseTap,
      tapMultiplier(state),
      rhythmMultiplier,
      manualTapMultiplier,
      feedbackTapMultiplier,
      if (jackpot) JACKPOT_MULTIPLIER else 1.0,
    )

    val charged = if (overdriveWasActive) {
      state.overdriveCharge
    } else {
      (state.overdriveCharge + OVERDRIVE_CHARGE_PER_TAP).coerceAtMost(OVERDRIVE_CHARGE_MAX)
    }
    val overdriveStarted = !overdriveWasActive && charged >= OVERDRIVE_CHARGE_MAX
    val nextCharge = if (overdriveStarted) 0.0 else charged
    val nextOverdrive = if (overdriveStarted) {
      overdriveDurationSeconds(state)
    } else if (
      overdriveWasActive && hasRunPick(state, IncrementalRunPick.FEEDBACK_LOOP)
    ) {
      state.overdriveRemainingSeconds + FEEDBACK_EXTENSION_SECONDS
    } else {
      state.overdriveRemainingSeconds
    }

    return IncrementalTapResult(
      state = state.copy(
        energy = saturatingAdd(state.energy, gainedEnergy),
        runEnergy = saturatingAdd(state.runEnergy, gainedEnergy),
        overdriveCharge = nextCharge,
        overdriveRemainingSeconds = nextOverdrive,
        taps = state.taps + 1,
      ),
      gainedEnergy = gainedEnergy,
      jackpot = jackpot,
      overdriveStarted = overdriveStarted,
    )
  }

  fun tick(
    state: IncrementalGameState,
    elapsedSeconds: Double,
  ): IncrementalGameState {
    if (elapsedSeconds <= 0.0) return state

    var remaining = elapsedSeconds
    var current = state

    if (current.overdriveRemainingSeconds > 0.0) {
      val overdriveSlice = min(remaining, current.overdriveRemainingSeconds)
      current = tickSegment(
        state = current,
        elapsedSeconds = overdriveSlice,
        productionMultiplier = overdriveProductionMultiplier(current),
      ).copy(
        overdriveRemainingSeconds = (current.overdriveRemainingSeconds - overdriveSlice)
          .coerceAtLeast(0.0),
      )
      remaining -= overdriveSlice
    }

    if (remaining > 0.0) {
      current = tickSegment(
        state = current,
        elapsedSeconds = remaining,
        productionMultiplier = 1.0,
      )
    }

    return current
  }

  fun productionPerSecond(state: IncrementalGameState): Double {
    val base = scaledProduct(
      state.generatorAmounts[IncrementalGeneratorType.SPARK.ordinal],
      milestoneMultiplier(state, IncrementalGeneratorType.SPARK),
      outputMultiplier(state),
      runAutomaticOutputMultiplier(state),
    )
    return saturatingMultiply(
      base,
      if (state.overdriveRemainingSeconds > 0.0) overdriveProductionMultiplier(state) else 1.0,
    )
  }

  fun isRunPickDue(state: IncrementalGameState): Boolean =
    state.runPicks.size < RUN_PICK_THRESHOLDS.size &&
      state.runEnergy >= RUN_PICK_THRESHOLDS[state.runPicks.size]

  fun availableRunPicks(state: IncrementalGameState): List<IncrementalRunPick> =
    IncrementalRunPick.entries.filterNot(state.runPicks::contains)

  fun selectRunPick(
    state: IncrementalGameState,
    pick: IncrementalRunPick,
  ): IncrementalGameState {
    if (!isRunPickDue(state) || pick in state.runPicks) return state
    return state.copy(runPicks = state.runPicks + pick)
  }

  fun purchaseCost(
    state: IncrementalGameState,
    type: IncrementalGeneratorType,
    amount: IncrementalPurchaseAmount,
  ): Double {
    val quantity = quantityFor(state, type, amount)
    return purchaseCost(state, type, quantity)
  }

  fun purchase(
    state: IncrementalGameState,
    type: IncrementalGeneratorType,
    amount: IncrementalPurchaseAmount,
  ): IncrementalGameState {
    val quantity = quantityFor(state, type, amount)
    if (quantity <= 0) return state

    val cost = purchaseCost(state, type, quantity)
    if (!cost.isFinite() || cost > state.energy) return state

    val index = type.ordinal
    val amounts = state.generatorAmounts.toMutableList()
    val purchases = state.generatorPurchases.toMutableList()
    amounts[index] += quantity.toDouble()
    purchases[index] += quantity

    return state.copy(
      energy = (state.energy - cost).coerceAtLeast(0.0),
      generatorAmounts = amounts,
      generatorPurchases = purchases,
    )
  }

  fun prestigeReward(state: IncrementalGameState): Int {
    if (state.runEnergy < PRESTIGE_THRESHOLD) return 0
    return floor(sqrt(state.runEnergy / PRESTIGE_THRESHOLD))
      .toInt()
      .coerceAtLeast(1)
  }

  fun nextPrestigeRewardEnergy(state: IncrementalGameState): Double {
    val nextReward = (prestigeReward(state) + 1).coerceAtLeast(1)
    return PRESTIGE_THRESHOLD * nextReward.toDouble().pow(2.0)
  }

  fun prestige(state: IncrementalGameState): IncrementalGameState {
    val reward = prestigeReward(state)
    if (reward <= 0) return state
    return newGame(
      prestigeCores = state.prestigeCores + reward,
      prestigeUpgradeLevels = state.prestigeUpgradeLevels,
    )
  }

  fun prestigeUpgradeLevel(
    state: IncrementalGameState,
    upgrade: IncrementalPrestigeUpgrade,
  ): Int = state.prestigeUpgradeLevels[upgrade.ordinal]

  fun prestigeUpgradeCost(
    state: IncrementalGameState,
    upgrade: IncrementalPrestigeUpgrade,
  ): Int? {
    val level = prestigeUpgradeLevel(state, upgrade)
    if (level >= upgrade.maxLevel) return null
    return if (upgrade.maxLevel == 1) upgrade.baseCost else upgrade.baseCost + level
  }

  fun isPrestigeUpgradeUnlocked(
    state: IncrementalGameState,
    upgrade: IncrementalPrestigeUpgrade,
  ): Boolean = totalPrestigeUpgradeLevels(state) >= upgrade.requiredTotalLevels

  fun purchasePrestigeUpgrade(
    state: IncrementalGameState,
    upgrade: IncrementalPrestigeUpgrade,
  ): IncrementalGameState {
    if (!isPrestigeUpgradeUnlocked(state, upgrade)) return state
    val cost = prestigeUpgradeCost(state, upgrade) ?: return state
    if (state.prestigeCores < cost) return state

    val levels = state.prestigeUpgradeLevels.toMutableList()
    levels[upgrade.ordinal] += 1

    val amounts = state.generatorAmounts.toMutableList()
    if (
      upgrade == IncrementalPrestigeUpgrade.STARTER_SPARK &&
      levels[upgrade.ordinal] == 1
    ) {
      amounts[IncrementalGeneratorType.SPARK.ordinal] =
        saturatingAdd(amounts[IncrementalGeneratorType.SPARK.ordinal], 1.0)
    }

    return state.copy(
      prestigeCores = state.prestigeCores - cost,
      prestigeUpgradeLevels = levels,
      generatorAmounts = amounts,
    )
  }

  fun totalPrestigeUpgradeLevels(state: IncrementalGameState): Int =
    state.prestigeUpgradeLevels.sum()

  fun milestoneInterval(state: IncrementalGameState): Int =
    if (prestigeUpgradeLevel(state, IncrementalPrestigeUpgrade.COMPACT_MILESTONES) > 0) {
      COMPACT_MILESTONE_INTERVAL
    } else {
      BASE_MILESTONE_INTERVAL
    }

  fun overdriveDurationSeconds(state: IncrementalGameState): Double {
    val baseDuration =
      BASE_OVERDRIVE_DURATION_SECONDS +
        prestigeUpgradeLevel(state, IncrementalPrestigeUpgrade.OVERDRIVE_CAPACITOR)
    return if (hasRunPick(state, IncrementalRunPick.OVERCLOCK)) {
      baseDuration * OVERCLOCK_DURATION_MULTIPLIER
    } else {
      baseDuration
    }
  }

  fun outputMultiplier(state: IncrementalGameState): Double =
    1.0 + prestigeUpgradeLevel(state, IncrementalPrestigeUpgrade.OUTPUT_AMPLIFIER) * 0.35

  fun cascadeMultiplier(state: IncrementalGameState): Double =
    1.0 + prestigeUpgradeLevel(state, IncrementalPrestigeUpgrade.CASCADE_TUNING) * 0.30

  fun tapMultiplier(state: IncrementalGameState): Double =
    1.0 + prestigeUpgradeLevel(state, IncrementalPrestigeUpgrade.TAP_RESONANCE) * 0.75

  private fun tickSegment(
    state: IncrementalGameState,
    elapsedSeconds: Double,
    productionMultiplier: Double,
  ): IncrementalGameState {
    if (elapsedSeconds <= 0.0) return state

    val spark = state.generatorAmounts[IncrementalGeneratorType.SPARK.ordinal]
    val reactor = state.generatorAmounts[IncrementalGeneratorType.REACTOR.ordinal]
    val starForge = state.generatorAmounts[IncrementalGeneratorType.STAR_FORGE.ordinal]
    val singularity = state.generatorAmounts[IncrementalGeneratorType.SINGULARITY.ordinal]

    val cascadeMultiplier = scaledProduct(
      cascadeMultiplier(state),
      if (hasRunPick(state, IncrementalRunPick.CASCADE_RESONANCE)) {
        CASCADE_RESONANCE_MULTIPLIER
      } else {
        1.0
      },
    )
    val reactorRate = scaledProduct(
      IncrementalGeneratorType.REACTOR.cascadePerSecond,
      milestoneMultiplier(state, IncrementalGeneratorType.REACTOR),
      cascadeMultiplier,
    )
    val starForgeRate = scaledProduct(
      IncrementalGeneratorType.STAR_FORGE.cascadePerSecond,
      milestoneMultiplier(state, IncrementalGeneratorType.STAR_FORGE),
      cascadeMultiplier,
    )
    val singularityRate = scaledProduct(
      IncrementalGeneratorType.SINGULARITY.cascadePerSecond,
      milestoneMultiplier(state, IncrementalGeneratorType.SINGULARITY),
      cascadeMultiplier,
    )

    val t2 = elapsedSeconds * elapsedSeconds
    val t3 = t2 * elapsedSeconds
    val t4 = t3 * elapsedSeconds

    val reactorFromStarForge = scaledProduct(starForge, starForgeRate, elapsedSeconds)
    val reactorFromSingularity = scaledProduct(singularity, singularityRate, starForgeRate, t2 / 2.0)
    val sparkFromReactor = scaledProduct(reactor, reactorRate, elapsedSeconds)
    val sparkFromStarForge = scaledProduct(starForge, starForgeRate, reactorRate, t2 / 2.0)
    val sparkFromSingularity =
      scaledProduct(singularity, singularityRate, starForgeRate, reactorRate, t3 / 6.0)

    val nextAmounts = listOf(
      saturatingAdd(
        spark,
        saturatingAdd(sparkFromReactor, saturatingAdd(sparkFromStarForge, sparkFromSingularity)),
      ),
      saturatingAdd(reactor, saturatingAdd(reactorFromStarForge, reactorFromSingularity)),
      saturatingAdd(starForge, scaledProduct(singularity, singularityRate, elapsedSeconds)),
      singularity,
    )

    val integratedSpark = saturatingAdd(
      saturatingMultiply(spark, elapsedSeconds),
      saturatingAdd(
        scaledProduct(reactor, reactorRate, t2 / 2.0),
        saturatingAdd(
          scaledProduct(starForge, starForgeRate, reactorRate, t3 / 6.0),
          scaledProduct(singularity, singularityRate, starForgeRate, reactorRate, t4 / 24.0),
        ),
      ),
    )
    val energyMultiplier = scaledProduct(
      milestoneMultiplier(state, IncrementalGeneratorType.SPARK),
      outputMultiplier(state),
      runAutomaticOutputMultiplier(state),
      productionMultiplier,
    )
    val gainedEnergy = saturatingMultiply(integratedSpark, energyMultiplier)

    return state.copy(
      energy = saturatingAdd(state.energy, gainedEnergy),
      runEnergy = saturatingAdd(state.runEnergy, gainedEnergy),
      generatorAmounts = nextAmounts,
    )
  }

  private fun quantityFor(
    state: IncrementalGameState,
    type: IncrementalGeneratorType,
    amount: IncrementalPurchaseAmount,
  ): Int = when (amount) {
    IncrementalPurchaseAmount.ONE -> 1
    IncrementalPurchaseAmount.TEN -> 10
    IncrementalPurchaseAmount.MAX -> maxAffordableQuantity(state, type)
  }

  private fun maxAffordableQuantity(
    state: IncrementalGameState,
    type: IncrementalGeneratorType,
  ): Int {
    if (state.energy <= 0.0) return 0

    val purchaseCount = state.generatorPurchases[type.ordinal]
    val firstCost = type.baseCost * type.costGrowth.pow(purchaseCount.toDouble())
    if (!firstCost.isFinite() || firstCost > state.energy) return 0

    val inside = 1.0 + state.energy * (type.costGrowth - 1.0) / firstCost
    if (!inside.isFinite()) return Int.MAX_VALUE

    return floor(ln(inside) / ln(type.costGrowth))
      .toLong()
      .coerceIn(1L, Int.MAX_VALUE.toLong())
      .toInt()
  }

  private fun purchaseCost(
    state: IncrementalGameState,
    type: IncrementalGeneratorType,
    quantity: Int,
  ): Double {
    if (quantity <= 0) return 0.0
    val purchaseCount = state.generatorPurchases[type.ordinal]
    val firstCost = type.baseCost * type.costGrowth.pow(purchaseCount.toDouble())
    val growth = type.costGrowth.pow(quantity.toDouble())
    return firstCost * (growth - 1.0) / (type.costGrowth - 1.0)
  }

  private fun milestoneMultiplier(
    state: IncrementalGameState,
    type: IncrementalGeneratorType,
  ): Double =
    MILESTONE_MULTIPLIER.pow(
      (state.generatorPurchases[type.ordinal] / milestoneInterval(state))
        .coerceIn(0, 300)
        .toDouble(),
    )

  private fun runAutomaticOutputMultiplier(state: IncrementalGameState): Double {
    val manualMultiplier = if (hasRunPick(state, IncrementalRunPick.MANUAL_OVERRIDE)) {
      MANUAL_OVERRIDE_AUTO_MULTIPLIER
    } else {
      1.0
    }
    val cascadeOutputMultiplier = if (hasRunPick(state, IncrementalRunPick.CASCADE_RESONANCE)) {
      CASCADE_RESONANCE_OUTPUT_MULTIPLIER
    } else {
      1.0
    }
    val lowPressureMultiplier = if (
      hasRunPick(state, IncrementalRunPick.LOW_PRESSURE) &&
      state.runEnergy > 0.0 &&
      state.energy <= state.runEnergy * LOW_PRESSURE_THRESHOLD
    ) {
      LOW_PRESSURE_MULTIPLIER
    } else {
      1.0
    }
    return scaledProduct(manualMultiplier, cascadeOutputMultiplier, lowPressureMultiplier)
  }

  fun overdriveProductionMultiplier(state: IncrementalGameState): Double =
    if (hasRunPick(state, IncrementalRunPick.OVERCLOCK)) {
      OVERCLOCK_MULTIPLIER
    } else {
      OVERDRIVE_MULTIPLIER
    }

  private fun hasRunPick(
    state: IncrementalGameState,
    pick: IncrementalRunPick,
  ): Boolean = pick in state.runPicks

  private fun normalizePrestigeLevels(levels: List<Int>): List<Int> =
    IncrementalPrestigeUpgrade.entries.mapIndexed { index, upgrade ->
      levels.getOrNull(index)?.coerceIn(0, upgrade.maxLevel) ?: 0
    }

  private fun saturatingAdd(a: Double, b: Double): Double {
    val value = a + b
    return if (value.isFinite()) value else Double.MAX_VALUE
  }

  private fun scaledProduct(vararg values: Double): Double =
    values.fold(1.0, ::saturatingMultiply)

  private fun saturatingMultiply(a: Double, b: Double): Double {
    val value = a * b
    return if (value.isFinite()) value else Double.MAX_VALUE
  }
}
