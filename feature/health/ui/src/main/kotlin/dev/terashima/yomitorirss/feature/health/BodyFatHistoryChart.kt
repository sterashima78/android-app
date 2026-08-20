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
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

internal data class BodyFatChartBounds(
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

internal fun latestBodyFatPercentage(measurements: List<BodyFatMeasurement>): Double? =
  measurements.maxByOrNull { it.time }?.percentage

@Composable
internal fun BodyFatHistoryChart(
  measurements: List<BodyFatMeasurement>,
  modifier: Modifier = Modifier,
) {
  val orderedMeasurements = remember(measurements) { measurements.sortedBy { it.time } }
  val bounds = remember(orderedMeasurements) { bodyFatChartBounds(orderedMeasurements) }
  val lineColor = MaterialTheme.colorScheme.primary
  val guideColor = MaterialTheme.colorScheme.outlineVariant
  val dateFormatter = remember { DateTimeFormatter.ofPattern("M/d H:mm") }
  val zoneId = remember { ZoneId.systemDefault() }

  Card(modifier = modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text("体脂肪率の推移", style = MaterialTheme.typography.titleMedium)
      if (orderedMeasurements.isEmpty()) {
        Text(
          "選択期間に体脂肪率の測定データはありません。",
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return@Column
      }

      val latest = orderedMeasurements.last()
      Text(
        "最新 ${formatBodyFatPercentage(latest.percentage)} %",
        style = MaterialTheme.typography.headlineSmall,
      )
      Text(
        "${orderedMeasurements.size} 件の測定値",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
        val valueRange = (bounds.max - bounds.min).coerceAtLeast(0.0001)
        val firstTime = orderedMeasurements.first().time.toEpochMilli()
        val lastTime = orderedMeasurements.last().time.toEpochMilli()
        val timeRange = (lastTime - firstTime).coerceAtLeast(1L)

        fun x(timeMillis: Long): Float = if (orderedMeasurements.size == 1) {
          size.width / 2f
        } else {
          size.width * ((timeMillis - firstTime).toDouble() / timeRange.toDouble()).toFloat()
        }

        fun y(percentage: Double): Float =
          size.height - (size.height * ((percentage - bounds.min) / valueRange)).toFloat()

        repeat(3) { index ->
          val guideY = size.height * index / 2f
          drawLine(
            color = guideColor,
            start = Offset(0f, guideY),
            end = Offset(size.width, guideY),
            strokeWidth = 1f,
          )
        }

        orderedMeasurements.zipWithNext().forEach { (previous, current) ->
          drawLine(
            color = lineColor,
            start = Offset(x(previous.time.toEpochMilli()), y(previous.percentage)),
            end = Offset(x(current.time.toEpochMilli()), y(current.percentage)),
            strokeWidth = 4f,
          )
        }
        orderedMeasurements.forEach { measurement ->
          drawCircle(
            color = lineColor,
            radius = 6f,
            center = Offset(x(measurement.time.toEpochMilli()), y(measurement.percentage)),
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          dateFormatter.format(orderedMeasurements.first().time.atZone(zoneId)),
          style = MaterialTheme.typography.labelSmall,
        )
        Text(
          dateFormatter.format(orderedMeasurements.last().time.atZone(zoneId)),
          style = MaterialTheme.typography.labelSmall,
        )
      }
      Text(
        "グラフ範囲 ${formatBodyFatPercentage(bounds.min)}〜${formatBodyFatPercentage(bounds.max)} %",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

internal fun formatBodyFatPercentage(value: Double): String =
  String.format(Locale.getDefault(), "%.1f", value)
