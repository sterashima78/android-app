package dev.terashima.yomitorirss.feature.podcast

import dev.terashima.yomitorirss.feature.audio.AudioPlaybackState
import dev.terashima.yomitorirss.feature.audio.AudioQueueItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PodcastChapterJumpTest {
  @Test
  fun `後ろのチャプターまでの移動数を返す`() {
    val state = playbackState(currentIndex = 0)

    assertEquals(2, podcastChapterJumpDistance(state, EPISODE_ID, 3))
  }

  @Test
  fun `前のチャプターまでの移動数を負数で返す`() {
    val state = playbackState(currentIndex = 2)

    assertEquals(-2, podcastChapterJumpDistance(state, EPISODE_ID, 1))
  }

  @Test
  fun `まだ準備されていないチャプターは移動対象にしない`() {
    val state = playbackState(currentIndex = 0, preparedChapterCount = 2)

    assertNull(podcastChapterJumpDistance(state, EPISODE_ID, 3))
  }

  private fun playbackState(
    currentIndex: Int,
    preparedChapterCount: Int = 3,
  ): AudioPlaybackState = AudioPlaybackState(
    items = (1..preparedChapterCount).map { chapterNumber ->
      AudioQueueItem(
        contentId = podcastChapterContentId(EPISODE_ID, chapterNumber),
        title = "chapter $chapterNumber",
        source = null,
      )
    },
    currentIndex = currentIndex,
  )

  private companion object {
    const val EPISODE_ID = "episode"
  }
}
