package dev.terashima.yomitorirss.core.airuntime

import org.junit.Assert.assertFalse
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
}
