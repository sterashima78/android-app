package dev.terashima.yomitorirss.feature.podcast.data

import dev.terashima.yomitorirss.feature.podcast.PodcastGenerationProvider
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastGenerationWorkerTest {
  @Test
  fun `当日の予定時刻前なら同日の実行まで待つ`() {
    val now = ZonedDateTime.of(2026, 9, 9, 6, 30, 0, 0, ZoneId.of("Asia/Tokyo"))

    assertEquals(30 * 60 * 1000L, nextRunDelayMillis(now, 7, 0))
  }

  @Test
  fun `予定時刻を過ぎていれば翌日まで待つ`() {
    val now = ZonedDateTime.of(2026, 9, 9, 7, 30, 0, 0, ZoneId.of("Asia/Tokyo"))

    assertEquals(23 * 60 * 60 * 1000L + 30 * 60 * 1000L, nextRunDelayMillis(now, 7, 0))
  }

  @Test
  fun `ローカル生成はローカルAI全体停止だけを尊重する`() {
    assertTrue(shouldSkipPodcastGeneration(PodcastGenerationProvider.LOCAL, localPaused = true, cloudPaused = false))
    assertFalse(shouldSkipPodcastGeneration(PodcastGenerationProvider.LOCAL, localPaused = false, cloudPaused = true))
  }

  @Test
  fun `クラウド生成はクラウドAI全体停止だけを尊重する`() {
    assertTrue(shouldSkipPodcastGeneration(PodcastGenerationProvider.CLOUD, localPaused = false, cloudPaused = true))
    assertFalse(shouldSkipPodcastGeneration(PodcastGenerationProvider.CLOUD, localPaused = true, cloudPaused = false))
  }
}
