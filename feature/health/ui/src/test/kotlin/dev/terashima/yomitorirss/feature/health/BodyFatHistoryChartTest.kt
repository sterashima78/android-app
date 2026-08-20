package dev.terashima.yomitorirss.feature.health

import java.time.Instant
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
}
