package dev.terashima.yomitorirss.feature.aitaskqueue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiTaskExecutionProviderLabelTest {
  @Test
  fun `AIタスクの実行先を表示用ラベルへ変換する`() {
    assertEquals("Local", taskExecutionProviderLabel(AiTaskExecutionProvider.LOCAL))
    assertEquals("ChatGPT", taskExecutionProviderLabel(AiTaskExecutionProvider.CHATGPT))
    assertNull(taskExecutionProviderLabel(null))
  }
}
