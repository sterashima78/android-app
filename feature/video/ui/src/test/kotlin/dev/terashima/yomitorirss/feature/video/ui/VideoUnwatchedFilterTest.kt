package dev.terashima.yomitorirss.feature.video.ui

import dev.terashima.yomitorirss.feature.video.VideoItem
import dev.terashima.yomitorirss.feature.video.VideoPlaybackState
import dev.terashima.yomitorirss.feature.video.VideoSavedState
import dev.terashima.yomitorirss.feature.video.VideoSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoUnwatchedFilterTest {
  @Test
  fun `未保存かつ未再生のprovider動画だけ未視聴になる`() {
    assertTrue(service().isUnwatched())
    assertFalse(service(saved = true).isUnwatched())
    assertFalse(service(positionMs = 1L).isUnwatched())
    assertFalse(service(completed = true).isUnwatched())
  }

  @Test
  fun `SMBとWebは常に保存済みなので未視聴にならない`() {
    assertFalse(item(VideoSource.SMB).isUnwatched())
    assertFalse(item(VideoSource.WEB).isUnwatched())
  }

  private fun service(
    saved: Boolean = false,
    positionMs: Long? = null,
    completed: Boolean = false,
  ): VideoItem = VideoItem(
    id = "service-${saved}-${positionMs}-${completed}",
    source = VideoSource.SERVICE,
    sourceId = "item",
    title = "video",
    updatedAtEpochMillis = 1L,
    savedState = if (saved) VideoSavedState(savedAtEpochMillis = 1L) else null,
    playbackState = positionMs?.let {
      VideoPlaybackState(
        positionMs = it,
        durationMs = 10_000L,
        lastPlayedAtEpochMillis = 1L,
        completed = completed,
      )
    } ?: if (completed) {
      VideoPlaybackState(
        positionMs = 0L,
        durationMs = 10_000L,
        lastPlayedAtEpochMillis = 1L,
        completed = true,
      )
    } else {
      null
    },
  )

  private fun item(source: VideoSource): VideoItem = VideoItem(
    id = source.name,
    source = source,
    sourceId = source.name,
    title = source.name,
    updatedAtEpochMillis = 1L,
  )
}
