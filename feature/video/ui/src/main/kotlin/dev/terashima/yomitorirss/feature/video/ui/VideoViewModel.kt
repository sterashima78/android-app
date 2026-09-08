package dev.terashima.yomitorirss.feature.video.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.terashima.yomitorirss.feature.library.SmbConnectionProfile
import dev.terashima.yomitorirss.feature.library.SmbConnectionProfileRepository
import dev.terashima.yomitorirss.feature.video.VideoFolder
import dev.terashima.yomitorirss.feature.video.VideoItem
import dev.terashima.yomitorirss.feature.video.VideoPlaybackTarget
import dev.terashima.yomitorirss.feature.video.VideoRepository
import dev.terashima.yomitorirss.feature.video.VideoSmbSource
import dev.terashima.yomitorirss.feature.video.VideoSource
import dev.terashima.yomitorirss.feature.video.VideoThumbnailResolver
import dev.terashima.yomitorirss.feature.video.WebVideoExtractorRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val NoOpVideoThumbnailResolver = object : VideoThumbnailResolver {
  override suspend fun resolve(item: VideoItem): String? = item.thumbnailUrl
}

data class VideoUiState(
  val items: List<VideoItem> = emptyList(),
  val folders: List<VideoFolder> = emptyList(),
  val smbProfiles: List<SmbConnectionProfile> = emptyList(),
  val smbSources: List<VideoSmbSource> = emptyList(),
  val extractorRules: List<WebVideoExtractorRule> = emptyList(),
  val loading: Boolean = true,
  val busy: Boolean = false,
  val busyMessage: String? = null,
  val message: String? = null,
)

data class VideoPlaybackSession(
  val item: VideoItem,
  val target: VideoPlaybackTarget,
  val isFullscreen: Boolean = false,
)

class VideoViewModel(
  private val repository: VideoRepository,
  private val smbConnectionProfiles: SmbConnectionProfileRepository,
  private val thumbnailResolver: VideoThumbnailResolver = NoOpVideoThumbnailResolver,
) : ViewModel() {
  private val mutableState = MutableStateFlow(VideoUiState())
  val state: StateFlow<VideoUiState> = mutableState.asStateFlow()

  private val mutablePlaybackSession = MutableStateFlow<VideoPlaybackSession?>(null)
  val playbackSession: StateFlow<VideoPlaybackSession?> = mutablePlaybackSession.asStateFlow()
  private val thumbnailRequests = mutableSetOf<String>()
  private var latestPlaybackPositionMs: Long = 0L

  init {
    reload()
  }

  fun reload() {
    viewModelScope.launch {
      runCatching { loadSnapshot() }.fold(
        onSuccess = ::showSnapshot,
        onFailure = { error ->
          mutableState.value = mutableState.value.copy(
            loading = false,
            busy = false,
            busyMessage = null,
            message = error.message ?: "動画一覧を読み込めませんでした",
          )
        },
      )
    }
  }

  fun addWeb(url: String) = launchMutation(
    fallbackMessage = "Web動画を追加できませんでした",
    busyMessage = "Web動画を追加中…",
  ) {
    repository.addWeb(url)
  }

  fun refreshSmb() = launchMutation("SMB動画を同期できませんでした") {
    try {
      repository.refreshSmb()
    } catch (error: Throwable) {
      throw IllegalStateException(smbSyncFailureMessage(error), error)
    }
  }

  fun ensureThumbnail(item: VideoItem) {
    if (item.source != VideoSource.SMB || !item.thumbnailUrl.isNullOrBlank()) return
    if (!thumbnailRequests.add(item.id)) return
    viewModelScope.launch {
      var retryItem: VideoItem? = null
      try {
        val thumbnailUrl = runCatching { thumbnailResolver.resolve(item) }
          .getOrNull()
          ?.takeIf(String::isNotBlank)
          ?: return@launch
        val current = mutableState.value
        val currentItem = current.items.firstOrNull { it.id == item.id } ?: return@launch
        if (currentItem.sourceId != item.sourceId || currentItem.sizeBytes != item.sizeBytes) {
          retryItem = currentItem
          return@launch
        }
        mutableState.value = current.copy(
          items = current.items.map { candidate ->
            if (candidate.id == item.id && candidate.thumbnailUrl.isNullOrBlank()) {
              candidate.copy(thumbnailUrl = thumbnailUrl)
            } else {
              candidate
            }
          },
        )
      } finally {
        thumbnailRequests.remove(item.id)
        retryItem?.let(::ensureThumbnail)
      }
    }
  }

  fun saveSmbSource(source: VideoSmbSource) = launchMutation("SMB同期場所を保存できませんでした") {
    val validProfile = smbConnectionProfiles.connectionProfiles().any { it.id == source.serverId }
    require(validProfile) { "SMB接続設定がありません" }
    repository.saveSmbSource(source)
  }

  fun deleteSmbSource(id: String) = launchMutation("SMB同期場所を削除できませんでした") {
    repository.deleteSmbSource(id)
  }

  fun saveVideo(item: VideoItem, folderId: String? = null) = launchMutation("動画を保存できませんでした") {
    repository.saveVideo(item.id, folderId)
  }

  fun removeSavedVideo(item: VideoItem) = launchMutation("動画の保存を解除できませんでした") {
    repository.removeSavedVideo(item.id)
  }

  fun saveFolder(folder: VideoFolder) = launchMutation("フォルダを保存できませんでした") {
    repository.saveFolder(folder)
  }

  fun deleteFolder(id: String) = launchMutation("フォルダを削除できませんでした") {
    repository.deleteFolder(id)
  }

  fun remove(item: VideoItem) = launchMutation("動画を削除できませんでした") {
    repository.remove(item.id)
  }

  fun setCompleted(item: VideoItem, completed: Boolean) = launchMutation("視聴状態を更新できませんでした") {
    repository.setCompleted(item.id, completed)
  }

  fun openPlayback(item: VideoItem, target: VideoPlaybackTarget) {
    require(target is VideoPlaybackTarget.Stream || target is VideoPlaybackTarget.Smb)
    latestPlaybackPositionMs = item.playbackState?.positionMs ?: 0L
    mutablePlaybackSession.value = VideoPlaybackSession(item, target)
  }

  fun setPlaybackFullscreen(isFullscreen: Boolean) {
    val session = mutablePlaybackSession.value ?: return
    mutablePlaybackSession.value = session.copy(isFullscreen = isFullscreen)
  }

  fun closePlayback() {
    mutablePlaybackSession.value = null
    latestPlaybackPositionMs = 0L
  }

  fun resumePositionMs(item: VideoItem): Long = if (mutablePlaybackSession.value?.item?.id == item.id) {
    latestPlaybackPositionMs
  } else {
    item.playbackState?.positionMs ?: 0L
  }

  fun savePlayback(item: VideoItem, positionMs: Long, durationMs: Long) {
    if (mutablePlaybackSession.value?.item?.id == item.id) {
      latestPlaybackPositionMs = positionMs.coerceAtLeast(0L)
    }
    viewModelScope.launch {
      runCatching {
        repository.updatePlayback(item.id, positionMs, durationMs)
      }.onFailure { error ->
        mutableState.value = mutableState.value.copy(
          message = error.message ?: "再生位置を保存できませんでした",
        )
      }
    }
  }

  fun saveExtractorRule(rule: WebVideoExtractorRule) = launchMutation("抽出ルールを保存できませんでした") {
    repository.saveExtractorRule(rule)
  }

  fun deleteExtractorRule(id: String) = launchMutation("抽出ルールを削除できませんでした") {
    repository.deleteExtractorRule(id)
  }

  fun dismissMessage() {
    mutableState.value = mutableState.value.copy(message = null)
  }

  private fun launchMutation(
    fallbackMessage: String,
    busyMessage: String? = null,
    block: suspend () -> Unit,
  ) {
    if (mutableState.value.busy) return
    mutableState.value = mutableState.value.copy(
      busy = true,
      busyMessage = busyMessage,
      message = null,
    )
    viewModelScope.launch {
      runCatching { block() }.fold(
        onSuccess = {
          runCatching { loadSnapshot() }.fold(
            onSuccess = ::showSnapshot,
            onFailure = { error ->
              mutableState.value = mutableState.value.copy(
                busy = false,
                busyMessage = null,
                message = error.message ?: fallbackMessage,
              )
            },
          )
        },
        onFailure = { error ->
          mutableState.value = mutableState.value.copy(
            busy = false,
            busyMessage = null,
            message = error.message ?: fallbackMessage,
          )
        },
      )
    }
  }

  private fun smbSyncFailureMessage(error: Throwable): String {
    val detail = generateSequence(error) { it.cause }
      .mapNotNull { cause -> cause.message?.trim()?.takeIf(String::isNotEmpty) }
      .firstOrNull()
      ?: error::class.simpleName
      ?: "原因を取得できませんでした"
    return "SMB動画を同期できませんでした: $detail"
  }

  private suspend fun loadSnapshot(): LoadedVideoState = LoadedVideoState(
    items = repository.items(),
    folders = repository.folders(),
    smbProfiles = smbConnectionProfiles.connectionProfiles(),
    smbSources = repository.smbSources(),
    extractorRules = repository.extractorRules(),
  )

  private fun showSnapshot(loaded: LoadedVideoState) {
    mutableState.value = mutableState.value.copy(
      items = loaded.items,
      folders = loaded.folders,
      smbProfiles = loaded.smbProfiles,
      smbSources = loaded.smbSources,
      extractorRules = loaded.extractorRules,
      loading = false,
      busy = false,
      busyMessage = null,
      message = null,
    )
  }

  class Factory(
    private val repository: VideoRepository,
    private val smbConnectionProfiles: SmbConnectionProfileRepository,
    private val thumbnailResolver: VideoThumbnailResolver = NoOpVideoThumbnailResolver,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      require(modelClass.isAssignableFrom(VideoViewModel::class.java))
      return VideoViewModel(repository, smbConnectionProfiles, thumbnailResolver) as T
    }
  }

  private data class LoadedVideoState(
    val items: List<VideoItem>,
    val folders: List<VideoFolder>,
    val smbProfiles: List<SmbConnectionProfile>,
    val smbSources: List<VideoSmbSource>,
    val extractorRules: List<WebVideoExtractorRule>,
  )
}
