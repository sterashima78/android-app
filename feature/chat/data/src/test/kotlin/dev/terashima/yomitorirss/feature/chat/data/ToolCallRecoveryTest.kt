package dev.terashima.yomitorirss.feature.chat.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCallRecoveryTest {
  @Test
  fun `LiteRTのtool call解析失敗を再試行対象として検出する`() {
    val error = IllegalStateException(
      "wrapper",
      IllegalArgumentException(
        "Status Code: 3. Message: Failed to parse tool calls from code block: call:test{}",
      ),
    )

    assertTrue(error.isGemmaToolCallParseFailure())
  }

  @Test
  fun `FC parserの内部エラー表現も検出する`() {
    val error = IllegalStateException("Failed to parse FC tool calls: ParseCancelledError")

    assertTrue(error.isGemmaToolCallParseFailure())
  }

  @Test
  fun `tool call以外のStatus Code 3は再試行対象にしない`() {
    val error = IllegalStateException("Status Code: 3. unrelated runtime error")

    assertFalse(error.isGemmaToolCallParseFailure())
  }
}
