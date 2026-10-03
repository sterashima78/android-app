package dev.terashima.yomitorirss.feature.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun IncrementalGameScreen(
  modifier: Modifier,
  state: IncrementalGameUiState,
  onBack: () -> Unit,
  onTick: (Double) -> Unit,
  onTap: () -> Unit,
  onPurchaseAmountChange: (IncrementalPurchaseAmount) -> Unit,
  onPurchase: (IncrementalGeneratorType) -> Unit,
  onPrestige: () -> Unit,
) {
  LaunchedEffect(Unit) {
    var lastNanos = System.nanoTime()
    while (isActive) {
      delay(50)
      val now = System.nanoTime()
      val elapsedSeconds = (now - lastNanos) / 1_000_000_000.0
      lastNanos = now
      onTick(elapsedSeconds)
    }
  }

  val game = state.game
  val production = IncrementalGame.productionPerSecond(game)
  val prestigeReward = IncrementalGame.prestigeReward(game)
  val isOverdrive = game.overdriveRemainingSeconds > 0.0

  Column(
    modifier = modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().height(44.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 6.dp)) {
        Text("‹ ゲーム")
      }
      Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("暴走炉", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
          "Prestige ${game.prestigePoints}  ×${formatIncrementalNumber(IncrementalGame.prestigeMultiplier(game))}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Spacer(modifier = Modifier.size(64.dp))
    }

    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        formatIncrementalNumber(game.energy),
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Black,
      )
      Text(
        "${formatIncrementalNumber(production)} / 秒",
        style = MaterialTheme.typography.titleSmall,
        color = if (isOverdrive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    Box(
      modifier = Modifier.fillMaxWidth(),
      contentAlignment = Alignment.Center,
    ) {
      Surface(
        onClick = onTap,
        modifier = Modifier.size(176.dp),
        shape = CircleShape,
        tonalElevation = if (isOverdrive) 12.dp else 5.dp,
        shadowElevation = if (isOverdrive) 16.dp else 8.dp,
        color = if (isOverdrive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
      ) {
        Column(
          modifier = Modifier.padding(18.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
        ) {
          Text(
            if (isOverdrive) "OVERDRIVE" else "TAP",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = if (isOverdrive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
          )
          Text(
            if (isOverdrive) {
              "×${formatIncrementalNumber(IncrementalGame.OVERDRIVE_MULTIPLIER)}"
            } else {
              "20タップで暴走"
            },
            style = MaterialTheme.typography.labelMedium,
            color = if (isOverdrive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
          )
          if (state.tapEventId > 0) {
            Text(
              if (state.lastTapWasJackpot) {
                "JACKPOT +${formatIncrementalNumber(state.lastTapReward)}"
              } else {
                "+${formatIncrementalNumber(state.lastTapReward)}"
              },
              modifier = Modifier.padding(top = 8.dp),
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
              color = if (isOverdrive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
              textAlign = TextAlign.Center,
            )
          }
        }
      }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(if (isOverdrive) "暴走残り" else "暴走ゲージ", style = MaterialTheme.typography.labelMedium)
        Text(
          if (isOverdrive) {
            String.format(Locale.US, "%.1f秒", game.overdriveRemainingSeconds)
          } else {
            "${game.overdriveCharge.toInt()} / ${IncrementalGame.OVERDRIVE_CHARGE_MAX.toInt()}"
          },
          style = MaterialTheme.typography.labelMedium,
        )
      }
      LinearProgressIndicator(
        progress = {
          if (isOverdrive) {
            (game.overdriveRemainingSeconds / IncrementalGame.OVERDRIVE_DURATION_SECONDS).toFloat()
          } else {
            (game.overdriveCharge / IncrementalGame.OVERDRIVE_CHARGE_MAX).toFloat()
          }
        },
        modifier = Modifier.fillMaxWidth(),
      )
    }

    PurchaseAmountSelector(
      selected = state.purchaseAmount,
      onSelected = onPurchaseAmountChange,
    )

    LazyColumn(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      contentPadding = PaddingValues(bottom = 12.dp),
    ) {
      items(IncrementalGeneratorType.entries) { type ->
        GeneratorCard(
          game = game,
          type = type,
          purchaseAmount = state.purchaseAmount,
          onPurchase = { onPurchase(type) },
        )
      }
      item {
        PrestigeCard(
          game = game,
          reward = prestigeReward,
          onPrestige = onPrestige,
        )
      }
    }
  }
}

@Composable
private fun PurchaseAmountSelector(
  selected: IncrementalPurchaseAmount,
  onSelected: (IncrementalPurchaseAmount) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
  ) {
    IncrementalPurchaseAmount.entries.forEach { amount ->
      FilterChip(
        selected = amount == selected,
        onClick = { onSelected(amount) },
        label = {
          Text(
            when (amount) {
              IncrementalPurchaseAmount.ONE -> "×1"
              IncrementalPurchaseAmount.TEN -> "×10"
              IncrementalPurchaseAmount.MAX -> "MAX"
            },
          )
        },
      )
    }
  }
}

@Composable
private fun GeneratorCard(
  game: IncrementalGameState,
  type: IncrementalGeneratorType,
  purchaseAmount: IncrementalPurchaseAmount,
  onPurchase: () -> Unit,
) {
  val cost = IncrementalGame.purchaseCost(game, type, purchaseAmount)
  val amount = game.generatorAmounts[type.ordinal]
  val purchases = game.generatorPurchases[type.ordinal]
  val canPurchase = cost.isFinite() && cost > 0.0 && game.energy >= cost

  Card {
    Column(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(generatorTitle(type), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
          Text(
            generatorDescription(type),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Text(
          formatIncrementalNumber(amount),
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
        )
      }

      Text(
        "購入数 $purchases ・ 25購入ごとに生産 ×10",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Button(
        onClick = onPurchase,
        enabled = canPurchase,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          if (purchaseAmount == IncrementalPurchaseAmount.MAX && !canPurchase) {
            "購入できません"
          } else {
            "${purchaseAmountLabel(purchaseAmount)}購入  ${formatIncrementalNumber(cost)}"
          },
        )
      }
    }
  }
}

@Composable
private fun PrestigeCard(
  game: IncrementalGameState,
  reward: Int,
  onPrestige: () -> Unit,
) {
  Card {
    Column(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text("Prestige", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      Text(
        "この周回の設備とエネルギーをリセットし、永久倍率を増やします。1ポイントごとに全生産 ×10。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (reward > 0) {
        Button(onClick = onPrestige, modifier = Modifier.fillMaxWidth()) {
          Text("+$reward Prestige Point")
        }
      } else {
        Text(
          "解禁まで ${formatIncrementalNumber((IncrementalGame.PRESTIGE_THRESHOLD - game.runEnergy).coerceAtLeast(0.0))}",
          style = MaterialTheme.typography.labelMedium,
        )
      }
    }
  }
}

private fun generatorTitle(type: IncrementalGeneratorType): String = when (type) {
  IncrementalGeneratorType.SPARK -> "スパーク"
  IncrementalGeneratorType.REACTOR -> "リアクター"
  IncrementalGeneratorType.STAR_FORGE -> "恒星炉"
  IncrementalGeneratorType.SINGULARITY -> "特異点"
}

private fun generatorDescription(type: IncrementalGeneratorType): String = when (type) {
  IncrementalGeneratorType.SPARK -> "エネルギーを直接生産"
  IncrementalGeneratorType.REACTOR -> "毎秒スパークを生成"
  IncrementalGeneratorType.STAR_FORGE -> "毎秒リアクターを生成"
  IncrementalGeneratorType.SINGULARITY -> "毎秒恒星炉を生成"
}

private fun purchaseAmountLabel(amount: IncrementalPurchaseAmount): String = when (amount) {
  IncrementalPurchaseAmount.ONE -> "×1 "
  IncrementalPurchaseAmount.TEN -> "×10 "
  IncrementalPurchaseAmount.MAX -> "MAX "
}

internal fun formatIncrementalNumber(value: Double): String {
  if (!value.isFinite()) return "∞"
  val magnitude = abs(value)
  if (magnitude < 1_000.0) {
    return when {
      magnitude >= 100.0 -> String.format(Locale.US, "%.0f", value)
      magnitude >= 10.0 -> String.format(Locale.US, "%.1f", value)
      else -> String.format(Locale.US, "%.2f", value)
    }
  }
  if (magnitude < 1_000_000.0) {
    return String.format(Locale.US, "%.2fK", value / 1_000.0)
  }

  val exponent = floor(log10(magnitude)).toInt()
  val mantissa = value / 10.0.pow(exponent.toDouble())
  return String.format(Locale.US, "%.3fe%d", mantissa, exponent)
}
