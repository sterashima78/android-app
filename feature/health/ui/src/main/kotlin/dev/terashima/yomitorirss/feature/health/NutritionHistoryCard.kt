package dev.terashima.yomitorirss.feature.health

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

internal enum class NutritionMetric(
  val shortLabel: String,
  val label: String,
  val unit: String,
) {
  ENERGY("熱量", "摂取カロリー", "kcal"),
  PROTEIN("P", "たんぱく質", "g"),
  FAT("F", "脂質", "g"),
  CARBOHYDRATE("C", "炭水化物", "g"),
}

@Composable
internal fun NutritionHistoryCard(
  intakes: List<DailyNutritionIntake>,
  modifier: Modifier = Modifier,
) {
  val orderedIntakes = remember(intakes) { intakes.sortedBy { it.date } }
  var selectedMetric by remember { mutableStateOf(NutritionMetric.ENERGY) }
  val standard = referenceRange(AdultMaleNutritionReference.standard, selectedMetric)
  val weightLoss = referenceRange(AdultMaleNutritionReference.weightLoss, selectedMetric)
  val actualColor = MaterialTheme.colorScheme.primary
  val standardColor = MaterialTheme.colorScheme.secondary
  val weightLossColor = MaterialTheme.colorScheme.tertiary
  val guideColor = MaterialTheme.colorScheme.outlineVariant
  val dateFormatter = remember { DateTimeFormatter.ofPattern("M/d") }

  Card(modifier = modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text("栄養摂取の推移", style = MaterialTheme.typography.titleMedium)
      Text(
        "Health Connect の食事記録を日ごとに合算します。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        NutritionMetric.entries.forEach { metric ->
          if (metric == selectedMetric) {
            Button(
              onClick = { selectedMetric = metric },
              modifier = Modifier.weight(1f),
            ) {
              Text(metric.shortLabel)
            }
          } else {
            OutlinedButton(
              onClick = { selectedMetric = metric },
              modifier = Modifier.weight(1f),
            ) {
              Text(metric.shortLabel)
            }
          }
        }
      }

      if (orderedIntakes.isEmpty()) {
        Text(
          "選択期間に栄養データはありません。あすけん等から Health Connect へ栄養情報が書き込まれているか確認してください。",
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        val latest = orderedIntakes.last()
        Text(
          "${dateFormatter.format(latest.date)}  ${formatMetric(metricValue(latest, selectedMetric), selectedMetric)} ${selectedMetric.unit}",
          style = MaterialTheme.typography.headlineSmall,
        )
        Text(
          "P ${formatOneDecimal(latest.proteinGrams)} g / F ${formatOneDecimal(latest.fatGrams)} g / C ${formatOneDecimal(latest.carbohydrateGrams)} g / ${formatWhole(latest.energyKcal)} kcal",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        NutritionChart(
          intakes = orderedIntakes,
          metric = selectedMetric,
          standard = standard,
          weightLoss = weightLoss,
          actualColor = actualColor,
          standardColor = standardColor,
          weightLossColor = weightLossColor,
          guideColor = guideColor,
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            dateFormatter.format(orderedIntakes.first().date),
            style = MaterialTheme.typography.labelSmall,
          )
          Text(
            dateFormatter.format(orderedIntakes.last().date),
            style = MaterialTheme.typography.labelSmall,
          )
        }
      }

      Text(
        "標準目安 ${formatRange(standard, selectedMetric)} ${selectedMetric.unit}",
        style = MaterialTheme.typography.bodySmall,
        color = standardColor,
      )
      Text(
        "減量参考 ${formatRange(weightLoss, selectedMetric)} ${selectedMetric.unit}",
        style = MaterialTheme.typography.bodySmall,
        color = weightLossColor,
      )
      Text(
        "基準は${AdultMaleNutritionReference.REFERENCE_AGE_LABEL}の「日本人の食事摂取基準（2025年版）」を使用。減量参考は標準エネルギーから500 kcal/日を差し引いた比較用の目安で、厚生労働省の個別減量基準ではありません。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        "たんぱく質の推奨量は65 g/日。グラフのP帯はエネルギー比13〜20%、F帯は20〜30%、C帯は50〜65%から換算しています。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun NutritionChart(
  intakes: List<DailyNutritionIntake>,
  metric: NutritionMetric,
  standard: NutritionReferenceRange,
  weightLoss: NutritionReferenceRange,
  actualColor: Color,
  standardColor: Color,
  weightLossColor: Color,
  guideColor: Color,
) {
  val values = intakes.map { metricValue(it, metric) }
  val maxValue = max(
    values.maxOrNull() ?: 0.0,
    max(standard.max, weightLoss.max),
  ).coerceAtLeast(1.0)
  val chartMax = maxValue * 1.1

  Canvas(modifier = Modifier.fillMaxWidth().height(190.dp)) {
    val firstDay = intakes.first().date.toEpochDay()
    val lastDay = intakes.last().date.toEpochDay()
    val dayRange = (lastDay - firstDay).coerceAtLeast(1L)

    fun x(day: Long): Float = if (intakes.size == 1) {
      size.width / 2f
    } else {
      size.width * ((day - firstDay).toDouble() / dayRange.toDouble()).toFloat()
    }

    fun y(value: Double): Float =
      size.height - (size.height * (value.coerceIn(0.0, chartMax) / chartMax)).toFloat()

    repeat(3) { index ->
      val guideY = size.height * index / 2f
      drawLine(
        color = guideColor,
        start = Offset(0f, guideY),
        end = Offset(size.width, guideY),
        strokeWidth = 1f,
      )
    }

    drawReferenceRange(standard, standardColor, ::y)
    drawReferenceRange(weightLoss, weightLossColor, ::y)

    intakes.zipWithNext().forEach { (previous, current) ->
      drawLine(
        color = actualColor,
        start = Offset(x(previous.date.toEpochDay()), y(metricValue(previous, metric))),
        end = Offset(x(current.date.toEpochDay()), y(metricValue(current, metric))),
        strokeWidth = 4f,
      )
    }
    intakes.forEach { intake ->
      drawCircle(
        color = actualColor,
        radius = 6f,
        center = Offset(x(intake.date.toEpochDay()), y(metricValue(intake, metric))),
      )
    }
  }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawReferenceRange(
  range: NutritionReferenceRange,
  color: Color,
  y: (Double) -> Float,
) {
  if (range.max - range.min < 0.001) {
    val lineY = y(range.min)
    drawLine(
      color = color,
      start = Offset(0f, lineY),
      end = Offset(size.width, lineY),
      strokeWidth = 3f,
    )
    return
  }

  val top = y(range.max)
  val bottom = y(range.min)
  drawRect(
    color = color.copy(alpha = 0.16f),
    topLeft = Offset(0f, top),
    size = Size(size.width, (bottom - top).coerceAtLeast(1f)),
  )
  drawLine(
    color = color,
    start = Offset(0f, top),
    end = Offset(size.width, top),
    strokeWidth = 2f,
  )
  drawLine(
    color = color,
    start = Offset(0f, bottom),
    end = Offset(size.width, bottom),
    strokeWidth = 2f,
  )
}

internal fun metricValue(intake: DailyNutritionIntake, metric: NutritionMetric): Double = when (metric) {
  NutritionMetric.ENERGY -> intake.energyKcal
  NutritionMetric.PROTEIN -> intake.proteinGrams
  NutritionMetric.FAT -> intake.fatGrams
  NutritionMetric.CARBOHYDRATE -> intake.carbohydrateGrams
}

internal fun referenceRange(
  profile: NutritionReferenceProfile,
  metric: NutritionMetric,
): NutritionReferenceRange = when (metric) {
  NutritionMetric.ENERGY -> profile.energyKcal
  NutritionMetric.PROTEIN -> profile.proteinGrams
  NutritionMetric.FAT -> profile.fatGrams
  NutritionMetric.CARBOHYDRATE -> profile.carbohydrateGrams
}

private fun formatRange(range: NutritionReferenceRange, metric: NutritionMetric): String =
  if (range.max - range.min < 0.001) {
    formatMetric(range.min, metric)
  } else {
    "${formatMetric(range.min, metric)}〜${formatMetric(range.max, metric)}"
  }

private fun formatMetric(value: Double, metric: NutritionMetric): String = when (metric) {
  NutritionMetric.ENERGY -> formatWhole(value)
  else -> formatOneDecimal(value)
}

private fun formatWhole(value: Double): String = String.format(Locale.getDefault(), "%.0f", value)

private fun formatOneDecimal(value: Double): String = String.format(Locale.getDefault(), "%.1f", value)
