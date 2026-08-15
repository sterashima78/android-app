package dev.terashima.yomitorirss.feature.summary

import org.junit.Assert.assertEquals
import org.junit.Test

class SummaryProgressLabelsTest {
  @Test
  fun `モデル準備中はモデル名を表示する`() {
    assertEquals(
      "Gemma を準備しています",
      summaryProgressLabel(stage = "preparing_model", modelName = "Gemma"),
    )
  }

  @Test
  fun `要約生成中にモデル名がなければ既定名を表示する`() {
    assertEquals(
      "モデル で要約を生成しています",
      summaryProgressLabel(stage = "generating_summary", modelName = null),
    )
  }

  @Test
  fun `未知の段階は元の値を保持する`() {
    assertEquals(
      "custom_stage: Gemma",
      summaryProgressLabel(stage = "custom_stage", modelName = "Gemma"),
    )
  }
}
