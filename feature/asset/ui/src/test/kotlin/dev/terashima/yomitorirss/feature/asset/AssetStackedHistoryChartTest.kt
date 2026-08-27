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
  fun `金額Y軸は読みやすい間隔へ切り上げる`() {
    val axis = buildAssetYAxis(
      AssetAreaChartData(emptyList(), minValue = 0f, maxValue = 12_000_000f),
      normalized = false,
    )

    assertEquals(0f, axis.minValue, DELTA)
    assertEquals(15_000_000f, axis.maxValue, DELTA)
    assertEquals(listOf(0f, 5_000_000f, 10_000_000f, 15_000_000f), axis.ticks)
    assertEquals("¥500万", formatAssetYAxisValue(5_000_000f, normalized = false))
    assertEquals("¥1.5億", formatAssetYAxisValue(150_000_000f, normalized = false))
  }

  @Test
  fun `構成比Y軸は割合を25パーセント刻みで表示する`() {
    val axis = buildAssetYAxis(
      AssetAreaChartData(emptyList(), minValue = 0f, maxValue = 1f),
      normalized = true,
    )

    assertEquals(listOf(0f, 0.25f, 0.5f, 0.75f, 1f), axis.ticks)
    assertEquals("50%", formatAssetYAxisValue(0.5f, normalized = true))
  }

  @Test
  fun `X軸は開始日から半年ごとの目盛りを作る`() {
    val points = listOf(
      point("2025-08-15", mapOf("資産" to 100L)),
      point("2026-08-20", mapOf("資産" to 120L)),
    )

    val ticks = buildAssetXAxisTicks(points)

    assertEquals(
      listOf(LocalDate.parse("2025-08-15"), LocalDate.parse("2026-02-15"), LocalDate.parse("2026-08-15")),
      ticks.map { it.date },
    )
    assertEquals(listOf("2025/8", "2026/2", "2026/8"), ticks.map { formatAssetXAxisValue(it.date) })
    assertEquals(0f, ticks.first().fraction, DELTA)
  }

  @Test
  fun `半年未満のX軸でも開始月と終了月を表示する`() {
    val points = listOf(
      point("2026-01-01", mapOf("資産" to 100L)),
      point("2026-04-01", mapOf("資産" to 120L)),
    )

    val ticks = buildAssetXAxisTicks(points)

    assertEquals(listOf("2026/1", "2026/4"), ticks.map { formatAssetXAxisValue(it.date) })
    assertEquals(0f, ticks.first().fraction, DELTA)
    assertEquals(1f, ticks.last().fraction, DELTA)
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
    assertBand(chart, "資産B", listOf(0.8f), listOf(1f,))
    assertBand(chart, "調整A", listOf(0f), listOf(-0.75f))
    assertBand(chart, "調整B", listOf(-0.75f), listOf(-1f))
  }

  @Test
  fun `円グラフは最新カテゴリの正の金額比を表示する`() {
    val slices = buildAssetPieSlices(mapOf("現金" to 60L, "投資" to 40L))

    assertEquals(listOf("現金", "投資"), slices.map { it.category })
    assertEquals(-90f, slices[0].startAngle, DELTA)
    assertEquals(216f, slices[0].sweepAngle, DELTA)
    assertEquals(126f, slices[1].startAngle, DELTA)
    assertEquals(144f, slices[1].sweepAngle, DELTA)
    assertEquals(360.0, slices.sumOf { it.sweepAngle.toDouble() }, DELTA.toDouble())
  }

  @Test
  fun `円グラフは0以下のカテゴリを除外する`() {
    val slices = buildAssetPieSlices(mapOf("資産" to 80L, "調整" to -20L, "ゼロ" to 0L))

    assertEquals(listOf("資産"), slices.map { it.category })
    assertEquals(80L, slices.single().value)
    assertEquals(360f, slices.single().sweepAngle, DELTA)
  }

  @Test
  fun `円グラフは正の金額がなければ空になる`() {
    val slices = buildAssetPieSlices(mapOf("調整" to -20L, "ゼロ" to 0L))

    assertEquals(emptyList<AssetPieSlice>(), slices)
  }

  @Test
  fun `24カテゴリまでは色が重複しない`() {
    val categories = (1..24).map { index -> "カテゴリ%02d".format(index) }

    val colors = buildAssetCategoryColorMap(categories)

    assertEquals(24, colors.size)
    assertEquals(24, colors.values.toSet().size)
  }

  @Test
  fun `カテゴリ色は入力順に依存しない`() {
    val categories = listOf("現金", "株式", "債券", "その他")

    val forward = buildAssetCategoryColorMap(categories)
    val reversed = buildAssetCategoryColorMap(categories.reversed())

    assertEquals(forward, reversed)
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
