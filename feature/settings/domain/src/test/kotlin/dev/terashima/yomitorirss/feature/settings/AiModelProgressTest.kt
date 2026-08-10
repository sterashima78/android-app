package dev.terashima.yomitorirss.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiModelProgressTest {
  @Test
  fun `ダウンロード進捗の残り時間は未算出でも表現できる`() {
    val progress = AiModelDownloadProgress(
      modelId = "model-1",
      phase = "download",
      downloadedBytes = 10,
      totalBytes = 100,
    )

    assertEquals(10L, progress.downloadedBytes)
    assertEquals(100L, progress.totalBytes)
    assertNull(progress.estimatedRemainingMillis)
  }
}
