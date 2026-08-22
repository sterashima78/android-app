package dev.terashima.yomitorirss.feature.health

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

internal data class BodyFatChartBounds(
  val min: Double,
  val max: Double,
)

internal data class WeightChartBounds(
  val min: Double,
  val max: Double,
)

internal fun bodyFatChartBounds(measurements: List<BodyFatMeasurement>): BodyFatChartBounds {
  if (measurements.isEmpty()) return BodyFatChartBounds(0.0, 100.0)

  val measuredMin = measurements.minOf { it.percentage }
  val measuredMax = measurements.maxOf { it.percentage }
  val padding = max((measuredMax - measuredMin) * 0.15, 1.0)
  var lower = max(0.0, measuredMin - padding)
  var upper = min(100.0, measuredMax + padding)

  if (upper - lower < 2.0) {
    when {
      lower <= 0.0 -> upper = min(100.0, lower + 2.0)
      upper >= 100.0 -> lower = max(0.0, upper - 2.0)
      else -> {
        val center = (lower + upper) / 2.0
        lower = center - 1.0
        upper = center + 1.0
      }
    }
  }
  return BodyFatChartBounds(lower, upper)
}

internal fun weightChartBounds(summaries: List<DailyHealthSummary>): WeightChartBounds {
  val weights = summaries.mapNotNull(DailyHealthSummary::averageWeightKg)
  if (weights.isEmpty()) return WeightChartBounds(0.0, 1.0)
  val measuredMin = weights.min()
  val measuredMax = weights.max()
  val padding = max((measuredMax - measuredMin) * 0.15, 0.5)
  return WeightChartBounds(
    min = max(0.0, measuredMin - padding),
    max = measuredMax + padding,
  )
}

internal fun latestBodyFatPercentage(measurements: List<BodyFatMeasurement>): Double? =
  measurements.maxByOrNull { it.time }?.percentage

@Composable
internal fun BodyCompositionHistoryChart(
  measurements: List<BodyFatMeasurement>,
  dailySummaries: List<DailyHealthSummary>,
  showWeight: Boolean,
  modifier: Modifier = Modifier,
) {
  val zoneId = remember { ZoneId.systemDefault() }
  val orderedMeasurements = remember(measurements) { measurements.sortedBy { it.time } }
  val weightPoints = remember(dailySummaries, showWeight) {
    if (!showWeight) {
      emptyList()
    } else {
      dailySummaries
        .mapNotNull { summary -> summary.averageWeightKg?.let { summary.date to it } }
        .sortedBy { it.first }
    }
  }
  val bodyFatBounds = remember(orderedMeasurements) { bodyFatChartBounds(orderedMeasurements) }
  val weightBounds = remember(dailySummaries, showWeight) { weightChartBounds(dailySummaries) }
  val weightColor = MaterialTheme.colorScheme.primary
  val bodyFatColor = MaterialTheme.colorScheme.tertiary
  val guideColor = MaterialTheme.colorScheme.outlineVariant
  val axisColor = MaterialTheme.colorScheme.outline
  val dateFormatter = remember { DateTimeFormatter.ofPattern("M/d") }
  val bodyFatPoints = remember(orderedMeasurements, zoneId) {
    orderedMeasurements.map { measurement ->
      measurement.time.atZone(zoneId).toLocalDate() to measurement.percentage
    }
  }
  val allDates = remember(bodyFatPoints, weightPoints) {
    (bodyFatPoints.map { it.first } + weightPoints.map { it.first }).sorted()
  }

  Card(modifier = modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        if (showWeight) "体重・体脂肪率の推移" else "体脂肪率の推移",
        style = MaterialTheme.typography.titleMedium,
      )
      if (bodyFatPoints.isEmpty() && weightPoints.isEmpty()) {
        Text(
          "選択期間に体重・体脂肪率の測定データはありません。",
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return@Column
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        if (showWeight) {
          Text("左軸 体重", style = MaterialTheme.typography.labelMedium, color = weightColor)
        }
        Text("右軸 体脂肪率", style = MaterialTheme.typography.labelMedium, color = bodyFatColor)
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          weightPoints.lastOrNull()?.let { "最新体重 ${formatWeightChartValue(it.second)} kg" }.orEmpty(),
          style = MaterialTheme.typography.bodySmall,
          color = weightColor,
        )
        Text(
          orderedMeasurements.lastOrNull()?.let { "最新体脂肪率 ${formatBodyFatPercentage(it.percentage)} %" }.orEmpty(),
          style = MaterialTheme.typography.bodySmall,
          color = bodyFatColor,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          if (weightPoints.isEmpty()) "" else "${formatWeightChartValue(weightBounds.max)} kg",
          style = MaterialTheme.typography.labelSmall,
          color = weightColor,
        )
        Text(
          if (bodyFatPoints.isEmpty()) "" else "${formatBodyFatPercentage(bodyFatBounds.max)} %",
          style = MaterialTheme.typography.labelSmall,
          color = bodyFatColor,
        )
      }

      Canvas(modifier = Modifier.fillMaxWidth().height(190.dp)) {
        val left = 8.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val plotWidth = (right - left).coerceAtLeast(1f)
        val firstDay = allDates.first().toEpochDay()
        val lastDay = allDates.last().toEpochDay()
        val dayRange = (lastDay - firstDay).coerceAtLeast(1L)

        fun x(date: LocalDate): Float = if (allDates.size == 1) {
          left + plotWidth / 2f
        } else {
          left + plotWidth * ((date.toEpochDay() - firstDay).toDouble() / dayRange.toDouble()).toFloat()
        }

        fun weightY(value: Double): Float {
          val range = (weightBounds.max - weightBounds.min).coerceAtLeast(0.0001)
          return size.height - (size.height * ((value - weightBounds.min) / range)).toFloat()
        }

        fun bodyFatY(value: Double): Float {
          val range = (bodyFatBounds.max - bodyFatBounds.min).coerceAtLeast(0.0001)
          return size.height - (size.height * ((value - bodyFatBounds.min) / range)).toFloat()
        }

        repeat(3) { index ->
          val guideY = size.height * index / 2f
          drawLine(
            color = guideColor,
            start = Offset(left, guideY),
            end = Offset(right, guideY),
            strokeWidth = 1f,
          )
        }
        if (weightPoints.isNotEmpty()) {
          drawLine(axisColor, Offset(left, 0f), Offset(left, size.height), strokeWidth = 2f)
          drawMetricSeries(weightPoints, weightColor, ::x, ::weightY)
        }
        if (bodyFatPoints.isNotEmpty()) {
          drawLine(axisColor, Offset(right, 0f), Offset(right, size.height), strokeWidth = 2f)
          drawMetricSeries(bodyFatPoints, bodyFatColor, ::x, ::bodyFatY)
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          if (weightPoints.isEmpty()) "" else "${formatWeightChartValue(weightBounds.min)} kg",
          style = MaterialTheme.typography.labelSmall,
          color = weightColor,
        )
        Text(
          if (bodyFatPoints.isEmpty()) "" else "${formatBodyFatPercentage(bodyFatBounds.min)} %",
          style = MaterialTheme.typography.labelSmall,
          color = bodyFatColor,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(dateFormatter.format(allDates.first()), style = MaterialTheme.typography.labelSmall)
        Text(dateFormatter.format(allDates.last()), style = MaterialTheme.typography.labelSmall)
      }
    }
  }
}

private fun DrawScope.drawMetricSeries(
  points: List<Pair<LocalDate, Double>>,
  color: Color,
  x: (LocalDate) -> Float,
  y: (Double) -> Float,
) {
  points.zipWithNext().forEach { (previous, current) ->
    drawLine(
      color = color,
      start = Offset(x(previous.first), y(previous.second)),
      end = Offset(x(current.first), y(current.second)),
      strokeWidth = 4f,
    )
  }
  points.forEach { point ->
    drawCircle(
      color = color,
      radius = 6f,
      center = Offset(x(point.first), y(point.second)),
    )
  }
}

internal fun formatBodyFatPercentage(value: Double): String =
  String.format(Locale.getDefault(), "%.1f", value)

internal fun formatWeightChartValue(value: Double): String =
  String.format(Locale.getDefault(), "%.1f", value)
