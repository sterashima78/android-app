package dev.terashima.yomitorirss.core.airuntime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessIsolatedLocalAiTextInferenceTest {
  @Test
  fun `2回目の生成完了で text inference process を再生成する`() {
    val policy = TextInferenceProcessBatchPolicy(maxRequests = 2)

    assertFalse(policy.requestFinished())
    assertTrue(policy.requestFinished())
  }

  @Test(expected = IllegalArgumentException::class)
  fun `batch size は正数だけを受け付ける`() {
    TextInferenceProcessBatchPolicy(maxRequests = 0)
  }

  @Test
  fun `Binder に渡すテキストは transaction budget より十分小さい上限にする`() {
    assertTrue(TEXT_INFERENCE_IPC_MAX_CHARS <= 128 * 1024)
  }

  @Test
  fun `subprocess は main process と別の model preferences を利用する`() {
    assertNotEquals("local_summary_models", TEXT_INFERENCE_CHILD_MODEL_PREFERENCES_NAME)
    assertNotEquals("local_context_benchmarks", TEXT_INFERENCE_CHILD_BENCHMARK_PREFERENCES_NAME)
  }

  @Test
  fun `main process で解決済みの context token 数を固定 mode に変換する`() {
    assertEquals(LocalContextSizeMode.CONTEXT_4K, isolatedContextSizeMode(4_096))
    assertEquals(LocalContextSizeMode.CONTEXT_8K, isolatedContextSizeMode(8_192))
    assertEquals(LocalContextSizeMode.CONTEXT_16K, isolatedContextSizeMode(16_384))
    assertEquals(LocalContextSizeMode.CONTEXT_32K, isolatedContextSizeMode(32_768))
  }

  @Test(expected = IllegalArgumentException::class)
  fun `未対応の context token 数は subprocess snapshot に渡さない`() {
    isolatedContextSizeMode(12_345)
  }
}
