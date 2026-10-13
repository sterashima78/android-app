package dev.terashima.yomitorirss.composition.podcast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PodcastPlaybackProgressTest {
  @Test
  fun `Podcastのチャプターitemからエピソードとチャプター番号を取り出す`() {
    assertEquals("episode-1" to 3, parsePodcastPlaybackChapter("podcast:episode-1:chapter:3"))
  }

  @Test
  fun `Podcast外のitemと不正な識別子は無視する`() {
    assertNull(parsePodcastPlaybackChapter(null))
    assertNull(parsePodcastPlaybackChapter("feed:article-1"))
    assertNull(parsePodcastPlaybackChapter("podcast:episode-1"))
    assertNull(parsePodcastPlaybackChapter("podcast:episode-1:chapter:0"))
    assertNull(parsePodcastPlaybackChapter("podcast:episode-1:chapter:invalid"))
    assertNull(parsePodcastPlaybackChapter("podcast::chapter:3"))
  }
}
