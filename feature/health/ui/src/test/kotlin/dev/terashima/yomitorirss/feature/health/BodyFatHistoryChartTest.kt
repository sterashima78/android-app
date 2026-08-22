package dev.terashima.yomitorirss.feature.health

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class BodyFatHistoryChartTest {
  @Test
  fun `測定値が空なら0から100を表示範囲にする`() {
    assertEquals(BodyFatChartBounds(0.0, 100.0), bodyFatChartBounds(emptyList()))
  }

  @Test
  fun `同じ体脂肪率だけでも表示範囲を確保する`() {
    val bounds = bodyFatChartBounds(
      listOf(BodyFatMeasurement(Instant.parse("2026-08-20T00:00:00Z"), 22.0)),
    )

    assertEquals(BodyFatChartBounds(21.0, 23.0), bounds)
  }

  @Test
  fun `上限付近では100パーセントを超えない`() {
    val bounds = bodyFatChartBounds(
      listOf(BodyFatMeasurement(Instant.parse("2026-08-20T00:00:00Z"), 100.0)),
    )

    assertEquals(BodyFatChartBounds(98.0, 100.0), bounds)
  }

  @Test
  fun `最新値は入力順ではなく測定日時で決める`() {
    val measurements = listOf(
      BodyFatMeasurement(Instant.parse("2026-08-20T12:00:00Z"), 21.5),
      BodyFatMeasurement(Instant.parse("2026-08-19T12:00:00Z"), 22.0),
    )

    assertEquals(21.5, latestBodyFatPercentage(measurements)!!, 0.0)
  }

  @Test
  fun `体重グラフは変化が小さくても表示範囲を確保する`() {
    val summaries = listOf(
      DailyHealthSummary(date = LocalDate.of(2026, 8, 18), averageWeightKg = 68.2),
      DailyHealthSummary(date = LocalDate.of(2026, 8, 19), averageWeightKg = 68.4),
    )

    assertEquals(WeightChartBounds(67.7, 68.9), weightChartBounds(summaries))
  }
}
