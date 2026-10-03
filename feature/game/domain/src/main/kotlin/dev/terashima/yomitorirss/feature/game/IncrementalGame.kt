package dev.terashima.yomitorirss.feature.game

import kotlin.math.floor
import kotlin.math.log
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow

enum class IncrementalGeneratorType(
  val baseCost: Double,
  val costGrowth: Double,
  val cascadePerSecond: Double,
) {
  SPARK(
    baseCost = 10.0,
    costGrowth = 1.15,
    cascadePerSecond = 0.0,
  ),
  REACTOR(
    baseCost = 10_000.0,
    costGrowth = 1.18,
    cascadePerSecond = 1.0,
  ),
  STAR_FORGE(
    baseCost = 100_000_000.0,
    costGrowth = 1.22,
    cascadePerSecond = 0.5,
  ),
  SINGULARITY(
    baseCost = 100_000_000_000_000.0,
    costGrowth = 1.25,
    cascadePerSecond = 0.25,
  ),
}

enum class IncrementalPurchaseAmount {
  ONE,
  TEN,
  MAX,
}

data class IncrementalGameState(
  val energy: Double = 0.0,
  val runEnergy: Double = 0.0,
  val generatorAmounts: List<Double> = List(IncrementalGeneratorType.entries.size) { 0.0 },
  val generatorPurchases: List<Int> = List(IncrementalGeneratorType.entries.size) { 0 },
  val overdriveCharge: Double = 0.0,
  val overdriveRemainingSeconds: Double = 0.0,
  val prestigePoints: Int = 0,
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
  const val OVERDRIVE_CHARGE_PER_TAP = 5.0
  const val OVERDRIVE_DURATION_SECONDS = 10.0
  const val OVERDRIVE_MULTIPLIER = 1_000.0
  const val JACKPOT_MULTIPLIER = 10_000.0
  const val PRESTIGE_THRESHOLD = 1_000_000_000_000.0

  fun newGame(prestigePoints: Int = 0): IncrementalGameState =
    IncrementalGameState(prestigePoints = prestigePoints.coerceAtLeast(0))

  fun tap(
    state: IncrementalGameState,
    jackpot: Boolean = false,
  ): IncrementalTapResult {
    val overdriveWasActive = state.overdriveRemainingSeconds > 0.0
    val prestigeMultiplier = prestigeMultiplier(state)
    val passivePerSecond = productionPerSecond(state)
    val baseTap = maxOf(prestigeMultiplier, passivePerSecond * 0.15)
    val gainedEnergy = saturatingMultiply(
      baseTap,
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
      OVERDRIVE_DURATION_SECONDS
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
        productionMultiplier = OVERDRIVE_MULTIPLIER,
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
    val base = saturatingMultiply(
      state.generatorAmounts[IncrementalGeneratorType.SPARK.ordinal],
      milestoneMultiplier(state.generatorPurchases[IncrementalGeneratorType.SPARK.ordinal]),
    )
    val withPrestige = saturatingMultiply(base, prestigeMultiplier(state))
    return saturatingMultiply(
      withPrestige,
      if (state.overdriveRemainingSeconds > 0.0) OVERDRIVE_MULTIPLIER else 1.0,
    )
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
    val exponent = log10(state.runEnergy)
    return (floor((exponent - 12.0) / 3.0).toInt() + 1).coerceAtLeast(1)
  }

  fun prestigeMultiplier(state: IncrementalGameState): Double =
    10.0.pow(state.prestigePoints.coerceIn(0, 300).toDouble())

  fun prestige(state: IncrementalGameState): IncrementalGameState {
    val reward = prestigeReward(state)
    if (reward <= 0) return state
    return newGame(prestigePoints = state.prestigePoints + reward)
  }

  private fun tickSegment(
    state: IncrementalGameState,
    elapsedSeconds: Double,
    productionMultiplier: Double,
  ): IncrementalGameState {
    if (elapsedSeconds <= 0.0) return state

    val amounts = state.generatorAmounts.toMutableList()

    for (type in IncrementalGeneratorType.entries.asReversed()) {
      if (type == IncrementalGeneratorType.SPARK) continue
      val index = type.ordinal
      val produced = saturatingMultiply(
        saturatingMultiply(
          amounts[index],
          type.cascadePerSecond * milestoneMultiplier(state.generatorPurchases[index]),
        ),
        elapsedSeconds,
      )
      amounts[index - 1] = saturatingAdd(amounts[index - 1], produced)
    }

    val sparkIndex = IncrementalGeneratorType.SPARK.ordinal
    val rawProduction = saturatingMultiply(
      amounts[sparkIndex],
      milestoneMultiplier(state.generatorPurchases[sparkIndex]),
    )
    val gainedEnergy = saturatingMultiply(
      saturatingMultiply(rawProduction, prestigeMultiplier(state)),
      saturatingMultiply(productionMultiplier, elapsedSeconds),
    )

    return state.copy(
      energy = saturatingAdd(state.energy, gainedEnergy),
      runEnergy = saturatingAdd(state.runEnergy, gainedEnergy),
      generatorAmounts = amounts,
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

    return floor(log(inside) / log(type.costGrowth))
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

  private fun milestoneMultiplier(purchases: Int): Double =
    10.0.pow((purchases / 25).coerceIn(0, 300).toDouble())

  private fun saturatingAdd(a: Double, b: Double): Double {
    val value = a + b
    return if (value.isFinite()) value else Double.MAX_VALUE
  }

  private fun saturatingMultiply(a: Double, b: Double): Double {
    val value = a * b
    return if (value.isFinite()) value else Double.MAX_VALUE
  }
}
