package dev.terashima.yomitorirss.feature.video.ui

import dev.terashima.yomitorirss.feature.video.VideoItem
import dev.terashima.yomitorirss.feature.video.VideoPlaybackState
import dev.terashima.yomitorirss.feature.video.VideoPlaybackTarget
import dev.terashima.yomitorirss.feature.video.VideoRepository
import dev.terashima.yomitorirss.feature.video.VideoSource
import dev.terashima.yomitorirss.feature.video.WebVideoExtractorRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VideoViewModelTest {
  @Test
  fun `Web動画追加中は進行中表示を公開し完了時に解除する`() = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    try {
      val repository = FakeVideoRepository()
      val viewModel = VideoViewModel(repository)
      advanceUntilIdle()

      viewModel.addWeb("https://example.com/video")

      assertTrue(viewModel.state.value.busy)
      assertEquals("Web動画を追加中…", viewModel.state.value.busyMessage)

      repository.addGate.complete(Unit)
      advanceUntilIdle()

      assertFalse(viewModel.state.value.busy)
      assertNull(viewModel.state.value.busyMessage)
      assertNull(viewModel.state.value.message)
      assertEquals(1, viewModel.state.value.items.size)
    } finally {
      Dispatchers.resetMain()
    }
  }

  @Test
  fun `Web動画追加に失敗すると進行中表示を解除してエラーを公開する`() = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    try {
      val repository = FakeVideoRepository(addFailure = IllegalStateException("追加に失敗しました"))
      val viewModel = VideoViewModel(repository)
      advanceUntilIdle()

      viewModel.addWeb("https://example.com/video")

      assertEquals("Web動画を追加中…", viewModel.state.value.busyMessage)
      repository.addGate.complete(Unit)
      advanceUntilIdle()

      assertFalse(viewModel.state.value.busy)
      assertNull(viewModel.state.value.busyMessage)
      assertEquals("追加に失敗しました", viewModel.state.value.message)
    } finally {
      Dispatchers.resetMain()
    }
  }

  @Test
  fun `再生セッションと全画面状態と最新位置をViewModel内に保持する`() = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    try {
      val repository = FakeVideoRepository()
      val viewModel = VideoViewModel(repository)
      advanceUntilIdle()
      val item = VideoItem(
        id = "video-1",
        source = VideoSource.WEB,
        sourceId = "https://example.com/video",
        title = "テスト動画",
        updatedAtEpochMillis = 1L,
        playbackState = VideoPlaybackState(
          positionMs = 4_000L,
          durationMs = 10_000L,
          lastPlayedAtEpochMillis = 1L,
          completed = false,
        ),
      )
      val target = VideoPlaybackTarget.Stream(
        url = "https://example.com/video.mp4",
        mimeType = "video/mp4",
        referrerUrl = "https://example.com",
      )

      viewModel.openPlayback(item, target)

      assertEquals(VideoPlaybackSession(item, target), viewModel.playbackSession.value)
      assertEquals(4_000L, viewModel.resumePositionMs(item))

      viewModel.setPlaybackFullscreen(true)
      viewModel.savePlayback(item, positionMs = 6_500L, durationMs = 10_000L)

      assertTrue(viewModel.playbackSession.value?.isFullscreen == true)
      assertEquals(6_500L, viewModel.resumePositionMs(item))
      advanceUntilIdle()

      viewModel.closePlayback()

      assertNull(viewModel.playbackSession.value)
      assertEquals(4_000L, viewModel.resumePositionMs(item))
    } finally {
      Dispatchers.resetMain()
    }
  }

  private class FakeVideoRepository(
    private val addFailure: Throwable? = null,
  ) : VideoRepository {
    val addGate = CompletableDeferred<Unit>()
    private val storedItems = mutableListOf<VideoItem>()

    override suspend fun items(): List<VideoItem> = storedItems.toList()

    override suspend fun addWeb(url: String): VideoItem {
      addGate.await()
      addFailure?.let { throw it }
      return VideoItem(
        id = "web-1",
        source = VideoSource.WEB,
        sourceId = url,
        title = "テスト動画",
        pageUrl = url,
        updatedAtEpochMillis = 1L,
      ).also(storedItems::add)
    }

    override suspend fun remove(id: String) = Unit

    override suspend fun refreshSmb(): Int = 0

    override suspend fun updatePlayback(
      videoId: String,
      positionMs: Long,
      durationMs: Long,
      completed: Boolean?,
    ) = Unit

    override suspend fun setCompleted(videoId: String, completed: Boolean) = Unit

    override fun extractorRules(): List<WebVideoExtractorRule> = emptyList()

    override fun saveExtractorRule(rule: WebVideoExtractorRule): WebVideoExtractorRule = rule

    override fun deleteExtractorRule(id: String) = Unit
  }
}
