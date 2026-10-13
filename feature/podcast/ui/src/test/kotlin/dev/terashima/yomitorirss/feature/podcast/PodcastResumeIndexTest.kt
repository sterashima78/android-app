package dev.terashima.yomitorirss.feature.podcast

import org.junit.Assert.assertEquals
import org.junit.Test

class PodcastResumeIndexTest {
  @Test
  fun `再生可能なエピソードは保存済みチャプターから再開する`() {
    assertEquals(2, podcastResumeIndex(PodcastEpisodeStatus.READY, 3, listOf(1, 2, 3, 4)))
  }

  @Test
  fun `チャプター記録がないまたは無効なら先頭から再開する`() {
    assertEquals(0, podcastResumeIndex(PodcastEpisodeStatus.READY, null, listOf(1, 2)))
    assertEquals(0, podcastResumeIndex(PodcastEpisodeStatus.READY, 99, listOf(1, 2)))
    assertEquals(0, podcastResumeIndex(PodcastEpisodeStatus.READY, 1, listOf(1)))
  }

  @Test
  fun `アーカイブ済みのエピソードは記録があっても先頭から再生する`() {
    assertEquals(0, podcastResumeIndex(PodcastEpisodeStatus.ARCHIVED, 3, listOf(1, 2, 3)))
  }
}
