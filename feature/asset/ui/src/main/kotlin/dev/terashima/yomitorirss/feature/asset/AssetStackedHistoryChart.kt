package dev.terashima.yomitorirss.feature.asset

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

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

@Composable
internal fun AssetStackedHistoryChart(
  points: List<AssetHistoryPoint>,
  normalized: Boolean,
) {
  val chartData = remember(points, normalized) { buildAssetAreaChartData(points, normalized) }
  val colorScheme = MaterialTheme.colorScheme
  val palette = listOf(
    colorScheme.primary,
    colorScheme.secondary,
    colorScheme.tertiary,
    colorScheme.error,
    colorScheme.inversePrimary,
    colorScheme.primaryContainer,
    colorScheme.secondaryContainer,
    colorScheme.tertiaryContainer,
  )
  val mutedColor = colorScheme.outlineVariant

  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
      val range = chartData.maxValue - chartData.minValue
      fun y(value: Float): Float =
        size.height - ((value - chartData.minValue) / range) * size.height

      val zeroY = y(0f).coerceIn(0f, size.height)
      drawLine(mutedColor, Offset(0f, zeroY), Offset(size.width, zeroY))

      chartData.bands.forEachIndexed { index, band ->
        val color = palette[index % palette.size]
        if (points.size == 1) {
          val startY = y(band.starts.single())
          val endY = y(band.ends.single())
          val top = minOf(startY, endY)
          val height = kotlin.math.abs(startY - endY)
          if (height > 0f) {
            drawRect(
              color = color.copy(alpha = 0.62f),
              topLeft = Offset(0f, top),
              size = Size(size.width, height),
            )
            drawLine(color, Offset(0f, endY), Offset(size.width, endY), strokeWidth = 1.5f)
          }
        } else {
          val path = Path()
          band.ends.forEachIndexed { pointIndex, value ->
            val x = size.width * pointIndex / (points.size - 1f)
            val pointY = y(value)
            if (pointIndex == 0) path.moveTo(x, pointY) else path.lineTo(x, pointY)
          }
          band.starts.indices.reversed().forEach { pointIndex ->
            val x = size.width * pointIndex / (points.size - 1f)
            path.lineTo(x, y(band.starts[pointIndex]))
          }
          path.close()
          drawPath(path, color.copy(alpha = 0.62f))
          drawPath(path, color, style = Stroke(width = 1.5f))
        }
      }
    }

    Row(modifier = Modifier.fillMaxWidth()) {
      Text(points.first().date.toString(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
      Text(points.last().date.toString(), style = MaterialTheme.typography.labelSmall)
    }

    Spacer(Modifier.height(2.dp))
    chartData.bands.forEachIndexed { index, band ->
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        val color = palette[index % palette.size]
        Canvas(Modifier.size(10.dp)) { drawRect(color) }
        Text(band.category, style = MaterialTheme.typography.labelSmall)
      }
    }
  }
}
