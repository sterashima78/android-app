package dev.terashima.yomitorirss.feature.asset

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class AssetStackedHistoryChartTest {
  @Test
  fun `金額表示ではカテゴリを積み上げる`() {
    val points = listOf(
      point("2026-08-01", mapOf("現金" to 60L, "投資" to 40L)),
      point("2026-08-02", mapOf("現金" to 50L, "投資" to 70L)),
    )

    val chart = buildAssetAreaChartData(points, normalized = false)

    assertEquals(0f, chart.minValue, DELTA)
    assertEquals(120f, chart.maxValue, DELTA)
    assertBand(chart, "投資", listOf(0f, 0f), listOf(40f, 70f))
    assertBand(chart, "現金", listOf(40f, 70f), listOf(100f, 120f))
  }

  @Test
  fun `100パーセント表示では日付ごとの構成比に正規化する`() {
    val points = listOf(
      point("2026-08-01", mapOf("現金" to 60L, "投資" to 40L)),
      point("2026-08-02", mapOf("現金" to 25L, "投資" to 75L)),
    )

    val chart = buildAssetAreaChartData(points, normalized = true)

    assertEquals(0f, chart.minValue, DELTA)
    assertEquals(1f, chart.maxValue, DELTA)
    assertBand(chart, "投資", listOf(0f, 0f), listOf(0.4f, 0.75f))
    assertBand(chart, "現金", listOf(0.4f, 0.75f), listOf(1f, 1f))
  }

  @Test
  fun `負数は正の積み上げと分離する`() {
    val points = listOf(
      point("2026-08-01", mapOf("資産" to 80L, "調整" to -20L)),
    )

    val chart = buildAssetAreaChartData(points, normalized = false)

    assertEquals(-20f, chart.minValue, DELTA)
    assertEquals(80f, chart.maxValue, DELTA)
    assertBand(chart, "資産", listOf(0f), listOf(80f))
    assertBand(chart, "調整", listOf(0f), listOf(-20f))
  }

  @Test
  fun `100パーセント表示では正負をそれぞれ正規化する`() {
    val points = listOf(
      point("2026-08-01", mapOf("資産A" to 80L, "資産B" to 20L, "調整A" to -30L, "調整B" to -10L)),
    )

    val chart = buildAssetAreaChartData(points, normalized = true)

    assertEquals(-1f, chart.minValue, DELTA)
    assertEquals(1f, chart.maxValue, DELTA)
    assertBand(chart, "資産A", listOf(0f), listOf(0.8f))
    assertBand(chart, "資産B", listOf(0.8f), listOf(1f))
    assertBand(chart, "調整A", listOf(0f), listOf(-0.75f))
    assertBand(chart, "調整B", listOf(-0.75f), listOf(-1f))
  }

  private fun point(date: String, byCategory: Map<String, Long>) = AssetHistoryPoint(
    date = LocalDate.parse(date),
    total = byCategory.values.sum(),
    byCategory = byCategory,
  )

  private fun assertBand(
    chart: AssetAreaChartData,
    category: String,
    starts: List<Float>,
    ends: List<Float>,
  ) {
    val band = chart.bands.single { it.category == category }
    assertEquals(starts.size, band.starts.size)
    assertEquals(ends.size, band.ends.size)
    starts.zip(band.starts).forEach { (expected, actual) -> assertEquals(expected, actual, DELTA) }
    ends.zip(band.ends).forEach { (expected, actual) -> assertEquals(expected, actual, DELTA) }
  }

  private companion object {
    const val DELTA = 0.0001f
  }
}
