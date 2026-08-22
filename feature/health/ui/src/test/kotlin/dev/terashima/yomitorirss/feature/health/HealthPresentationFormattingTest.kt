package dev.terashima.yomitorirss.feature.health

import org.junit.Assert.assertEquals
import org.junit.Test

class HealthPresentationFormattingTest {
  @Test
  fun `睡眠時間は分ではなく時間単位で表示する`() {
    assertEquals("7.5", formatSleepHours(450))
  }

  @Test
  fun `睡眠データがない場合はプレースホルダーを表示する`() {
    assertEquals("—", formatSleepHours(null))
  }
}
