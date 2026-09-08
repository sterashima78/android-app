package dev.terashima.yomitorirss.feature.video.ui

import dev.terashima.yomitorirss.feature.library.SmbConnectionProfile
import dev.terashima.yomitorirss.feature.library.SmbConnectionProfileRepository
import dev.terashima.yomitorirss.feature.library.SmbLibraryLocation
import dev.terashima.yomitorirss.feature.video.VideoFolder
import dev.terashima.yomitorirss.feature.video.VideoItem
import dev.terashima.yomitorirss.feature.video.VideoPlaybackState
import dev.terashima.yomitorirss.feature.video.VideoPlaybackTarget
import dev.terashima.yomitorirss.feature.video.VideoProvider
import dev.terashima.yomitorirss.feature.video.VideoProviderRefreshResult
import dev.terashima.yomitorirss.feature.video.VideoProviderRepository
import dev.terashima.yomitorirss.feature.video.VideoProviderVideo
import dev.terashima.yomitorirss.feature.video.VideoRepository
import dev.terashima.yomitorirss.feature.video.VideoSmbSource
import dev.terashima.yomitorirss.feature.video.VideoSource
import dev.terashima.yomitorirss.feature.video.VideoSubscription
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
      val viewModel = videoViewModel(repository)
      advanceUntilIdle()

      viewModel.addWeb("https://example.invalid/video")

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
      val viewModel = videoViewModel(repository)
      advanceUntilIdle()

      viewModel.addWeb("https://example.invalid/video")

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
  fun `SMB同期失敗のmessageが空でも原因種別を公開する`() = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    try {
      val repository = FakeVideoRepository(smbFailure = IllegalStateException())
      val viewModel = videoViewModel(repository)
      advanceUntilIdle()

      viewModel.refreshSmb()
      advanceUntilIdle()

      assertFalse(viewModel.state.value.busy)
      assertEquals(
        "SMB動画を同期できませんでした: IllegalStateException",
        viewModel.state.value.message,
      )
    } finally {
      Dispatchers.resetMain()
    }
  }

  @Test
  fun `SMB同期失敗では内側の具体的なmessageを優先する`() = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    try {
      val repository = FakeVideoRepository(
        smbFailure = IllegalStateException(null, IllegalArgumentException("接続処理に失敗しました")),
      )
      val viewModel = videoViewModel(repository)
      advanceUntilIdle()

      viewModel.refreshSmb()
      advanceUntilIdle()

      assertEquals(
        "SMB動画を同期できませんでした: 接続処理に失敗しました",
        viewModel.state.value.message,
      )
    } finally {
      Dispatchers.resetMain()
    }
  }

  @Test
  fun `再生セッションと全画面状態と最新位置をViewModel内に保持する`() = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    try {
      val repository = FakeVideoRepository()
      val viewModel = videoViewModel(repository)
      advanceUntilIdle()
      val item = VideoItem(
        id = "video-1",
        source = VideoSource.WEB,
        sourceId = "https://example.invalid/video",
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
        url = "https://cdn.example.invalid/video.mp4",
        mimeType = "video/mp4",
        referrerUrl = "https://example.invalid/",
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

  @Test
  fun `保存フォルダをsnapshotへ読み込む`() = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    try {
      val repository = FakeVideoRepository().apply {
        storedFolders += VideoFolder(id = "folder-1", name = "あとで見る")
      }
      val viewModel = videoViewModel(repository)
      advanceUntilIdle()

      assertEquals(listOf("folder-1"), viewModel.state.value.folders.map(VideoFolder::id))
    } finally {
      Dispatchers.resetMain()
    }
  }

  private fun videoViewModel(repository: VideoRepository): VideoViewModel = VideoViewModel(
    repository = repository,
    providerRepository = FakeVideoProviderRepository,
    smbConnectionProfiles = FakeSmbConnectionProfileRepository,
  )

  private class FakeVideoRepository(
    private val addFailure: Throwable? = null,
    private val smbFailure: Throwable? = null,
  ) : VideoRepository {
    val addGate = CompletableDeferred<Unit>()
    private val storedItems = mutableListOf<VideoItem>()
    val storedFolders = mutableListOf<VideoFolder>()

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

    override suspend fun refreshSmb(): Int {
      smbFailure?.let { throw it }
      return 0
    }

    override fun smbSources(): List<VideoSmbSource> = emptyList()
    override fun saveSmbSource(source: VideoSmbSource): VideoSmbSource = source
    override fun deleteSmbSource(id: String) = Unit
    override fun folders(): List<VideoFolder> = storedFolders.toList()
    override fun saveFolder(folder: VideoFolder): VideoFolder = folder
    override fun deleteFolder(id: String) = Unit
    override fun saveVideo(videoId: String, folderId: String?) = Unit
    override fun removeSavedVideo(videoId: String) = Unit

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

private object FakeVideoProviderRepository : VideoProviderRepository {
  override fun providers(): List<VideoProvider> = emptyList()
  override fun saveProvider(provider: VideoProvider): VideoProvider = provider
  override fun deleteProvider(id: String) = Unit
  override fun subscriptions(providerId: String?): List<VideoSubscription> = emptyList()
  override suspend fun subscribe(providerId: String, sourceUrl: String): VideoSubscription = error("unused")
  override suspend fun unsubscribe(subscriptionId: String) = Unit
  override suspend fun refreshProviders(providerId: String?): VideoProviderRefreshResult =
    VideoProviderRefreshResult(0, 0, 0)

  override fun unreadVideos(): List<VideoProviderVideo> = emptyList()
  override fun watchLaterVideos(): List<VideoProviderVideo> = emptyList()
  override fun historyVideos(limit: Int): List<VideoProviderVideo> = emptyList()
  override fun markRead(videoId: String) = Unit
  override fun markUnread(videoId: String) = Unit
  override fun setWatchLater(videoId: String, watchLater: Boolean) = Unit
  override fun markAllRead(providerId: String?) = Unit
}

private object FakeSmbConnectionProfileRepository : SmbConnectionProfileRepository {
  override suspend fun connectionProfiles(): List<SmbConnectionProfile> = emptyList()

  override suspend fun saveConnectionProfile(
    profile: SmbConnectionProfile,
    password: String?,
  ): SmbConnectionProfile = profile

  override suspend fun deleteConnectionProfile(profileId: String) = Unit
  override suspend fun libraryLocations(): List<SmbLibraryLocation> = emptyList()
  override suspend fun saveLibraryLocation(location: SmbLibraryLocation): SmbLibraryLocation = location
  override suspend fun deleteLibraryLocation(serverId: String) = Unit
}
