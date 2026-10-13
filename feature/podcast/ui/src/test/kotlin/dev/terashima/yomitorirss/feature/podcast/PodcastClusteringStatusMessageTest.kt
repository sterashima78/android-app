package dev.terashima.yomitorirss.feature.podcast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PodcastClusteringStatusMessageTest {
  @Test
  fun `fallback states are visible to the user`() {
    assertEquals(
      "分類処理に失敗したため、記事を1件ずつ分けました",
      podcastClusteringStatusMessage(PodcastClusteringStatus.FALLBACK_INFERENCE_ERROR),
    )
    assertEquals(
      "分類結果が不正だったため、記事を1件ずつ分けました",
      podcastClusteringStatusMessage(PodcastClusteringStatus.FALLBACK_INVALID_OUTPUT),
    )
  }

  @Test
  fun `successful and skipped classification do not produce warnings`() {
    assertNull(podcastClusteringStatusMessage(PodcastClusteringStatus.SUCCESS))
    assertNull(podcastClusteringStatusMessage(PodcastClusteringStatus.SKIPPED))
    assertNull(podcastClusteringStatusMessage(null))
  }
}
