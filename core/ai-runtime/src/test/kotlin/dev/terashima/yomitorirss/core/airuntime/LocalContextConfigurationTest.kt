package dev.terashima.yomitorirss.core.airuntime

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalContextConfigurationTest {
  @Test
  fun `未計測の自動設定は8Kを使う`() {
    assertEquals(
      8_192,
      resolveContextTokens(
        mode = LocalContextSizeMode.AUTO,
        maxSupportedContextTokens = 32_768,
        benchmarkRecommendation = null,
      ),
    )
  }

  @Test
  fun `自動設定は保存済みの推奨値を使う`() {
    assertEquals(
      16_384,
      resolveContextTokens(
        mode = LocalContextSizeMode.AUTO,
        maxSupportedContextTokens = 32_768,
        benchmarkRecommendation = 16_384,
      ),
    )
  }

  @Test
  fun `手動設定はモデル上限を超えない`() {
    assertEquals(
      16_384,
      resolveContextTokens(
        mode = LocalContextSizeMode.CONTEXT_32K,
        maxSupportedContextTokens = 16_384,
        benchmarkRecommendation = null,
      ),
    )
  }

  @Test
  fun `安全と判定された最大コンテキストを推奨する`() {
    val samples = listOf(
      sample(4_096, safe = true),
      sample(8_192, safe = true),
      sample(16_384, safe = true),
      sample(32_768, safe = false),
    )

    assertEquals(16_384, chooseRecommendedContextTokens(samples))
  }

  @Test
  fun `安全圏がない場合は8K以下で成功した最大値へ戻す`() {
    val samples = listOf(
      sample(4_096, safe = false),
      sample(8_192, safe = false),
      sample(16_384, safe = false),
    )

    assertEquals(8_192, chooseRecommendedContextTokens(samples))
  }

  @Test
  fun `失敗したコンテキストは自動推奨に使わない`() {
    val samples = listOf(
      sample(8_192, safe = true),
      sample(16_384, safe = false, error = "OOM"),
    )

    assertEquals(8_192, chooseRecommendedContextTokens(samples))
  }

  private fun sample(
    contextTokens: Int,
    safe: Boolean,
    error: String? = null,
  ) = LocalContextBenchmarkSample(
    contextTokens = contextTokens,
    requestedPrefillTokens = contextTokens / 2,
    initTimeMillis = 100,
    inferenceTimeMillis = 200,
    baselinePssBytes = 1,
    peakPssBytes = 2,
    peakNativePssBytes = 1,
    peakGraphicsPssBytes = 0,
    minimumAvailableMemoryBytes = 3,
    safe = safe,
    error = error,
  )
}
