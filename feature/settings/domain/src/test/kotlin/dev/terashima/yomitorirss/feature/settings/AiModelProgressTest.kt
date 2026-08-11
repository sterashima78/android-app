package dev.terashima.yomitorirss.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiModelProgressTest {
  @Test
  fun `ダウンロード進捗の残り時間は未算出でも表現できる`() {
    val progress = AiModelDownloadProgress(
      modelId = "model-1",
      phase = "downloading",
      downloadedBytes = 10,
      totalBytes = 100,
    )

    assertEquals(10L, progress.downloadedBytes)
    assertEquals(100L, progress.totalBytes)
    assertNull(progress.estimatedRemainingMillis)
  }

  @Test
  fun `ダウンロード進捗は実行中フェーズだけアクティブになる`() {
    listOf("queued", "downloading", "verifying").forEach { phase ->
      assertTrue(progress(phase).isActive)
    }
    listOf("completed", "failed", "cancelled").forEach { phase ->
      assertFalse(progress(phase).isActive)
    }
  }

  private fun progress(phase: String) = AiModelDownloadProgress(
    modelId = "model-1",
    phase = phase,
    downloadedBytes = 100,
    totalBytes = 100,
  )
}
