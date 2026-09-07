package dev.terashima.yomitorirss.feature.video.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.terashima.yomitorirss.feature.library.SmbConnectionProfile
import dev.terashima.yomitorirss.feature.library.SmbConnectionProfileRepository
import dev.terashima.yomitorirss.feature.video.VideoItem
import dev.terashima.yomitorirss.feature.video.VideoRepository
import dev.terashima.yomitorirss.feature.video.VideoSmbSource
import dev.terashima.yomitorirss.feature.video.WebVideoExtractorRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class VideoUiState(
  val items: List<VideoItem> = emptyList(),
  val smbProfiles: List<SmbConnectionProfile> = emptyList(),
  val smbSources: List<VideoSmbSource> = emptyList(),
  val extractorRules: List<WebVideoExtractorRule> = emptyList(),
  val loading: Boolean = true,
  val busy: Boolean = false,
  val message: String? = null,
)

class VideoViewModel(
  private val repository: VideoRepository,
  private val smbConnectionProfiles: SmbConnectionProfileRepository,
) : ViewModel() {
  private val mutableState = MutableStateFlow(VideoUiState())
  val state: StateFlow<VideoUiState> = mutableState.asStateFlow()

  init {
    reload()
  }

  fun reload() {
    viewModelScope.launch {
      runCatching(::loadSnapshot).fold(
        onSuccess = ::showSnapshot,
        onFailure = { error ->
          mutableState.value = mutableState.value.copy(
            loading = false,
            busy = false,
            message = error.message ?: "動画一覧を読み込めませんでした",
          )
        },
      )
    }
  }

  fun addWeb(url: String) = launchMutation("Web動画を追加できませんでした") {
    repository.addWeb(url)
  }

  fun refreshSmb() = launchMutation("SMB動画を同期できませんでした") {
    repository.refreshSmb()
  }

  fun saveSmbSource(source: VideoSmbSource) = launchMutation("SMB同期場所を保存できませんでした") {
    val validProfile = smbConnectionProfiles.connectionProfiles().any { it.id == source.serverId }
    require(validProfile) { "SMB接続設定がありません" }
    repository.saveSmbSource(source)
  }

  fun deleteSmbSource(id: String) = launchMutation("SMB同期場所を削除できませんでした") {
    repository.deleteSmbSource(id)
  }

  fun remove(item: VideoItem) = launchMutation("動画を削除できませんでした") {
    repository.remove(item.id)
  }

  fun setCompleted(item: VideoItem, completed: Boolean) = launchMutation("視聴状態を更新できませんでした") {
    repository.setCompleted(item.id, completed)
  }

  fun savePlayback(item: VideoItem, positionMs: Long, durationMs: Long) {
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
    block: suspend () -> Unit,
  ) {
    if (mutableState.value.busy) return
    mutableState.value = mutableState.value.copy(busy = true, message = null)
    viewModelScope.launch {
      runCatching { block() }.fold(
        onSuccess = {
          runCatching(::loadSnapshot).fold(
            onSuccess = ::showSnapshot,
            onFailure = { error ->
              mutableState.value = mutableState.value.copy(
                busy = false,
                message = error.message ?: fallbackMessage,
              )
            },
          )
        },
        onFailure = { error ->
          mutableState.value = mutableState.value.copy(
            busy = false,
            message = error.message ?: fallbackMessage,
          )
        },
      )
    }
  }

  private suspend fun loadSnapshot(): LoadedVideoState = LoadedVideoState(
    items = repository.items(),
    smbProfiles = smbConnectionProfiles.connectionProfiles(),
    smbSources = repository.smbSources(),
    extractorRules = repository.extractorRules(),
  )

  private fun showSnapshot(loaded: LoadedVideoState) {
    mutableState.value = mutableState.value.copy(
      items = loaded.items,
      smbProfiles = loaded.smbProfiles,
      smbSources = loaded.smbSources,
      extractorRules = loaded.extractorRules,
      loading = false,
      busy = false,
      message = null,
    )
  }

  class Factory(
    private val repository: VideoRepository,
    private val smbConnectionProfiles: SmbConnectionProfileRepository,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      require(modelClass.isAssignableFrom(VideoViewModel::class.java))
      return VideoViewModel(repository, smbConnectionProfiles) as T
    }
  }

  private data class LoadedVideoState(
    val items: List<VideoItem>,
    val smbProfiles: List<SmbConnectionProfile>,
    val smbSources: List<VideoSmbSource>,
    val extractorRules: List<WebVideoExtractorRule>,
  )
}
