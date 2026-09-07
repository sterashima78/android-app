package dev.terashima.yomitorirss.feature.video.ui

import dev.terashima.yomitorirss.feature.video.VideoItem
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
