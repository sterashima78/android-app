package dev.terashima.yomitorirss.feature.asset

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

internal data class AssetAreaBand(
  val category: String,
  val starts: List<Float>,
  val ends: List<Float>,
)

internal data class AssetAreaChartData(
  val bands: List<AssetAreaBand>,
  val minValue: Float,
  val maxValue: Float,
)

internal data class AssetYAxis(
  val minValue: Float,
  val maxValue: Float,
  val ticks: List<Float>,
)

internal data class AssetXAxisTick(
  val date: LocalDate,
  val fraction: Float,
)

internal data class AssetPieSlice(
  val category: String,
  val value: Long,
  val startAngle: Float,
  val sweepAngle: Float,
)

internal val ASSET_CHART_PALETTE = listOf(
  Color(0xFF4F8EF7),
  Color(0xFFFF8A3D),
  Color(0xFF47B881),
  Color(0xFFE85D75),
  Color(0xFF9B6EF3),
  Color(0xFF24B8C4),
  Color(0xFFE0B43C),
  Color(0xFFEF6BC1),
  Color(0xFF7AA35A),
  Color(0xFFB7794D),
  Color(0xFF6C8CF5),
  Color(0xFFFF6B5E),
  Color(0xFF53C7A2),
  Color(0xFFC47BEA),
  Color(0xFF3BA6E8),
  Color(0xFFD8C33F),
  Color(0xFFB56B8D),
  Color(0xFF5FA0A0),
  Color(0xFFA87B4F),
  Color(0xFF8BC34A),
  Color(0xFFEE7B30),
  Color(0xFF5B7DB1),
  Color(0xFFD065A6),
  Color(0xFF6BBF59),
)

internal fun buildAssetCategoryColorMap(categories: Collection<String>): Map<String, Color> =
  categories
    .distinct()
    .sorted()
    .mapIndexed { index, category -> category to ASSET_CHART_PALETTE[index % ASSET_CHART_PALETTE.size] }
    .toMap()

internal fun buildAssetPieSlices(byCategory: Map<String, Long>): List<AssetPieSlice> {
  val positiveEntries = byCategory.entries
    .filter { it.value > 0L }
    .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
  if (positiveEntries.isEmpty()) return emptyList()

  val total = positiveEntries.sumOf { it.value.toDouble() }
  var usedSweep = 0f
  return positiveEntries.mapIndexed { index, entry ->
    val sweepAngle = if (index == positiveEntries.lastIndex) {
      360f - usedSweep
    } else {
      (entry.value.toDouble() / total * 360.0).toFloat()
    }
    AssetPieSlice(
      category = entry.key,
      value = entry.value,
      startAngle = -90f + usedSweep,
      sweepAngle = sweepAngle,
    ).also {
      usedSweep += sweepAngle
    }
  }
}

internal fun buildAssetAreaChartData(
  points: List<AssetHistoryPoint>,
  normalized: Boolean,
): AssetAreaChartData {
  val categories = points
    .flatMap { it.byCategory.keys }
    .distinct()
    .sorted()
  if (categories.isEmpty()) return AssetAreaChartData(emptyList(), 0f, 1f)

  val starts = categories.associateWith { mutableListOf<Float>() }
  val ends = categories.associateWith { mutableListOf<Float>() }
  var minValue = 0f
  var maxValue = 0f

  points.forEach { point ->
    val positiveTotal = point.byCategory.values.filter { it > 0L }.sumOf { it.toDouble() }
    val negativeTotal = point.byCategory.values.filter { it < 0L }.sumOf { -it.toDouble() }
    var positiveStack = 0.0
    var negativeStack = 0.0

    categories.forEach { category ->
      val rawValue = (point.byCategory[category] ?: 0L).toDouble()
      val value = when {
        !normalized -> rawValue
        rawValue > 0.0 && positiveTotal > 0.0 -> rawValue / positiveTotal
        rawValue < 0.0 && negativeTotal > 0.0 -> rawValue / negativeTotal
        else -> 0.0
      }
      val start = if (value >= 0.0) positiveStack else negativeStack
      val end = start + value
      if (value >= 0.0) positiveStack = end else negativeStack = end

      val startFloat = start.toFloat()
      val endFloat = end.toFloat()
      starts.getValue(category) += startFloat
      ends.getValue(category) += endFloat
      minValue = minOf(minValue, startFloat, endFloat)
      maxValue = maxOf(maxValue, startFloat, endFloat)
    }
  }

  if (minValue == maxValue) maxValue = minValue + 1f
  return AssetAreaChartData(
    bands = categories.map { category ->
      AssetAreaBand(
        category = category,
        starts = starts.getValue(category),
        ends = ends.getValue(category),
      )
    },
    minValue = minValue,
    maxValue = maxValue,
  )
}

internal fun buildAssetYAxis(
  chartData: AssetAreaChartData,
  normalized: Boolean,
): AssetYAxis {
  if (normalized) {
    return if (chartData.minValue < 0f) {
      AssetYAxis(
        minValue = -1f,
        maxValue = 1f,
        ticks = listOf(-1f, -0.5f, 0f, 0.5f, 1f),
      )
    } else {
      AssetYAxis(
        minValue = 0f,
        maxValue = 1f,
        ticks = listOf(0f, 0.25f, 0.5f, 0.75f, 1f),
      )
    }
  }

  val min = chartData.minValue.toDouble()
  val max = chartData.maxValue.toDouble()
  val rawStep = (max - min) / 4.0
  val step = maxOf(1.0, niceAssetAxisStep(rawStep))
  val axisMin = floor(min / step) * step
  var axisMax = ceil(max / step) * step
  if (axisMin == axisMax) axisMax += step
  val intervalCount = ((axisMax - axisMin) / step).roundToInt().coerceAtLeast(1)
  return AssetYAxis(
    minValue = axisMin.toFloat(),
    maxValue = axisMax.toFloat(),
    ticks = (0..intervalCount).map { index -> (axisMin + step * index).toFloat() },
  )
}

internal fun buildAssetXAxisTicks(points: List<AssetHistoryPoint>): List<AssetXAxisTick> {
  if (points.isEmpty()) return emptyList()
  val startDate = points.first().date
  val endDate = points.last().date
  if (!endDate.isAfter(startDate)) return listOf(AssetXAxisTick(startDate, 0f))

  val dates = buildList {
    var date = startDate
    while (!date.isAfter(endDate)) {
      add(date)
      date = date.plusMonths(6)
    }
    val lastTick = last()
    val remainingDays = ChronoUnit.DAYS.between(lastTick, endDate)
    if (size == 1 || remainingDays >= 90) add(endDate)
  }.distinctBy { it.year to it.monthValue }

  return dates.map { date ->
    AssetXAxisTick(
      date = date,
      fraction = assetDateFraction(date, startDate, endDate),
    )
  }
}

internal fun assetDateFraction(
  date: LocalDate,
  startDate: LocalDate,
  endDate: LocalDate,
): Float {
  val totalDays = ChronoUnit.DAYS.between(startDate, endDate)
  if (totalDays <= 0L) return 0f
  val elapsedDays = ChronoUnit.DAYS.between(startDate, date)
  return (elapsedDays.toDouble() / totalDays.toDouble()).toFloat().coerceIn(0f, 1f)
}

internal fun formatAssetYAxisValue(value: Float, normalized: Boolean): String {
  if (normalized) return "${(value * 100f).roundToInt()}%"

  val normalizedValue = if (abs(value) < 0.0001f) 0.0 else value.toDouble()
  val sign = if (normalizedValue < 0.0) "-" else ""
  val absoluteValue = abs(normalizedValue)
  return when {
    absoluteValue >= 100_000_000.0 -> "$sign¥${formatAssetAxisUnit(absoluteValue / 100_000_000.0)}億"
    absoluteValue >= 10_000.0 -> "$sign¥${formatAssetAxisUnit(absoluteValue / 10_000.0)}万"
    else -> "$sign¥${NumberFormat.getIntegerInstance(Locale.JAPAN).format(absoluteValue.roundToLong())}"
  }
}

internal fun formatAssetXAxisValue(date: LocalDate): String = "${date.year}/${date.monthValue}"

private fun niceAssetAxisStep(rawStep: Double): Double {
  if (!rawStep.isFinite() || rawStep <= 0.0) return 1.0
  val exponent = floor(log10(rawStep))
  val magnitude = 10.0.pow(exponent)
  val fraction = rawStep / magnitude
  val niceFraction = when {
    fraction <= 1.0 -> 1.0
    fraction <= 2.0 -> 2.0
    fraction <= 2.5 -> 2.5
    fraction <= 5.0 -> 5.0
    else -> 10.0
  }
  return niceFraction * magnitude
}

private fun formatAssetAxisUnit(value: Double): String {
  val tenths = (value * 10.0).roundToLong()
  return if (tenths % 10L == 0L) {
    (tenths / 10L).toString()
  } else {
    "${tenths / 10L}.${tenths % 10L}"
  }
}

@Composable
internal fun AssetStackedHistoryChart(
  points: List<AssetHistoryPoint>,
  normalized: Boolean,
  categoryColors: Map<String, Color>,
) {
  val chartData = remember(points, normalized) { buildAssetAreaChartData(points, normalized) }
  val yAxis = remember(chartData, normalized) { buildAssetYAxis(chartData, normalized) }
  val xTicks = remember(points) { buildAssetXAxisTicks(points) }
  val latestPieSlices = remember(points) { buildAssetPieSlices(points.last().byCategory) }
  val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
  val axisColor = MaterialTheme.colorScheme.outline

  Column {
    Row(modifier = Modifier.fillMaxWidth()) {
      BoxWithConstraints(modifier = Modifier.width(72.dp).height(200.dp)) {
        val labelHeight = 16.dp
        val availableHeight = maxHeight - labelHeight
        val range = yAxis.maxValue - yAxis.minValue
        yAxis.ticks.forEach { tick ->
          val fraction = 1f - ((tick - yAxis.minValue) / range)
          val y = (maxHeight * fraction - labelHeight / 2).coerceIn(0.dp, availableHeight)
          Text(
            text = formatAssetYAxisValue(tick, normalized),
            modifier = Modifier.fillMaxWidth().height(labelHeight).offset(y = y),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.End,
            maxLines = 1,
          )
        }
      }

      Column(modifier = Modifier.weight(1f)) {
        Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
          val range = yAxis.maxValue - yAxis.minValue
          fun y(value: Float): Float =
            size.height - ((value - yAxis.minValue) / range) * size.height

          fun x(pointIndex: Int): Float {
            val startDate = points.first().date
            val endDate = points.last().date
            return if (endDate.isAfter(startDate)) {
              size.width * assetDateFraction(points[pointIndex].date, startDate, endDate)
            } else if (points.size > 1) {
              size.width * pointIndex / (points.size - 1f)
            } else {
              0f
            }
          }

          chartData.bands.forEachIndexed { index, band ->
            val color = categoryColors[band.category] ?: ASSET_CHART_PALETTE[index % ASSET_CHART_PALETTE.size]
            if (points.size == 1) {
              val startY = y(band.starts.single())
              val endY = y(band.ends.single())
              val top = minOf(startY, endY)
              val height = abs(startY - endY)
              if (height > 0f) {
                drawRect(
                  color = color.copy(alpha = 0.68f),
                  topLeft = Offset(0f, top),
                  size = Size(size.width, height),
                )
                drawLine(color, Offset(0f, endY), Offset(size.width, endY), strokeWidth = 1.5f)
              }
            } else {
              val path = Path()
              band.ends.forEachIndexed { pointIndex, value ->
                val pointX = x(pointIndex)
                val pointY = y(value)
                if (pointIndex == 0) path.moveTo(pointX, pointY) else path.lineTo(pointX, pointY)
              }
              band.starts.indices.reversed().forEach { pointIndex ->
                path.lineTo(x(pointIndex), y(band.starts[pointIndex]))
              }
              path.close()
              drawPath(path, color.copy(alpha = 0.68f))
              drawPath(path, color, style = Stroke(width = 1.5f))
            }
          }

          yAxis.ticks.forEach { tick ->
            val gridY = y(tick).coerceIn(0f, size.height)
            val isZero = abs(tick) < 0.0001f
            drawLine(
              color = if (isZero) axisColor else gridColor,
              start = Offset(0f, gridY),
              end = Offset(size.width, gridY),
              strokeWidth = if (isZero) 1.5.dp.toPx() else 1.dp.toPx(),
            )
          }

          xTicks.forEach { tick ->
            val tickX = size.width * tick.fraction
            drawLine(
              color = axisColor,
              start = Offset(tickX, size.height - 5.dp.toPx()),
              end = Offset(tickX, size.height),
              strokeWidth = 1.dp.toPx(),
            )
          }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(24.dp)) {
          val labelWidth = 56.dp
          val maxOffset = maxWidth - labelWidth
          xTicks.forEach { tick ->
            val x = (maxWidth * tick.fraction - labelWidth / 2).coerceIn(0.dp, maxOffset)
            Text(
              text = formatAssetXAxisValue(tick.date),
              modifier = Modifier.width(labelWidth).offset(x = x),
              style = MaterialTheme.typography.labelSmall,
              textAlign = TextAlign.Center,
              maxLines = 1,
            )
          }
        }
      }
    }

    if (latestPieSlices.isNotEmpty()) {
      Spacer(Modifier.height(20.dp))
      Text("最新スナップショットの構成比", style = MaterialTheme.typography.titleMedium)
      Spacer(Modifier.height(8.dp))
      Canvas(modifier = Modifier.fillMaxWidth().height(220.dp)) {
        val diameter = minOf(size.width, size.height)
        val topLeft = Offset(
          x = (size.width - diameter) / 2f,
          y = (size.height - diameter) / 2f,
        )
        val pieSize = Size(diameter, diameter)
        latestPieSlices.forEachIndexed { index, slice ->
          val color = categoryColors[slice.category] ?: ASSET_CHART_PALETTE[index % ASSET_CHART_PALETTE.size]
          drawArc(
            color = color,
            startAngle = slice.startAngle,
            sweepAngle = slice.sweepAngle,
            useCenter = true,
            topLeft = topLeft,
            size = pieSize,
          )
        }
      }
      if (points.last().byCategory.values.any { it < 0L }) {
        Text(
          "円グラフは正の金額のみで構成比を表示しています。",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
