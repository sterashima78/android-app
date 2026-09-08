package dev.terashima.yomitorirss.feature.video.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoPlayerDoubleTapSeekTest {
  @Test
  fun `左半分のダブルタップでは15秒戻す`() {
    assertEquals(
      45_000L,
      videoPlayerDoubleTapSeekPositionMs(
        currentPositionMs = 60_000L,
        durationMs = 120_000L,
        tapX = 100f,
        playerWidth = 400f,
      ),
    )
  }

  @Test
  fun `右半分のダブルタップでは15秒進める`() {
    assertEquals(
      75_000L,
      videoPlayerDoubleTapSeekPositionMs(
        currentPositionMs = 60_000L,
        durationMs = 120_000L,
        tapX = 300f,
        playerWidth = 400f,
      ),
    )
  }

  @Test
  fun `15秒戻しは動画先頭で止める`() {
    assertEquals(
      0L,
      videoPlayerDoubleTapSeekPositionMs(
        currentPositionMs = 5_000L,
        durationMs = 120_000L,
        tapX = 100f,
        playerWidth = 400f,
      ),
    )
  }

  @Test
  fun `15秒送りは動画末尾で止める`() {
    assertEquals(
      120_000L,
      videoPlayerDoubleTapSeekPositionMs(
        currentPositionMs = 115_000L,
        durationMs = 120_000L,
        tapX = 300f,
        playerWidth = 400f,
      ),
    )
  }

  @Test
  fun `duration未確定時も右半分のダブルタップで15秒進める`() {
    assertEquals(
      75_000L,
      videoPlayerDoubleTapSeekPositionMs(
        currentPositionMs = 60_000L,
        durationMs = -1L,
        tapX = 300f,
        playerWidth = 400f,
      ),
    )
  }
}
