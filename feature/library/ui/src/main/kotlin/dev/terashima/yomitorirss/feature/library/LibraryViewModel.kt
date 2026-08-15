package dev.terashima.yomitorirss.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
  val initialized: Boolean = false,
  val syncing: Boolean = false,
  val importingSource: LibrarySource? = null,
  val smbSyncing: Boolean = false,
  val smbSettingsBusy: Boolean = false,
  val smbServers: List<SmbServerSettings> = emptyList(),
  val books: List<LibraryBook> = emptyList(),
  val hiddenBooks: List<LibraryBook> = emptyList(),
  val sourceStates: Map<LibrarySource, LibrarySourceState> = emptyMap(),
  val message: String? = null,
)

class LibraryViewModel(
  private val repository: LibraryRepository,
  private val smbRepository: SmbLibraryRepository? = null,
) : ViewModel() {
  private val _state = MutableStateFlow(LibraryUiState())
  val state: StateFlow<LibraryUiState> = _state.asStateFlow()

  init {
    refresh()
  }

  fun syncGooglePlayBooks(accessToken: String, accountLabel: String?) {
    if (isBusy()) return
    viewModelScope.launch(Dispatchers.IO) {
      _state.update { it.copy(syncing = true) }
      runCatching { repository.syncGooglePlayBooks(accessToken, accountLabel) }
        .onSuccess { result ->
          loadSnapshot(
            message = "Google Play Books から ${result.importedCount} 冊を同期しました",
          )
        }
        .onFailure(::showError)
    }
  }

  fun syncSmbLibrary() {
    val smb = smbRepository ?: return
    if (isBusy()) return
    viewModelScope.launch(Dispatchers.IO) {
      _state.update { it.copy(smbSyncing = true) }
      runCatching { smb.sync() }
        .onSuccess { result ->
          loadSnapshot(message = "ファイルサーバから ${result.importedCount} 冊を同期しました")
        }
        .onFailure(::showError)
    }
  }

  fun saveSmbServer(settings: SmbServerSettings, password: String?) {
    val smb = smbRepository ?: return
    if (isBusy()) return
    viewModelScope.launch(Dispatchers.IO) {
      _state.update { it.copy(smbSettingsBusy = true) }
      runCatching { smb.saveServer(settings, password) }
        .onSuccess { saved ->
          loadSnapshot(message = "${saved.name} のSMB設定を保存しました")
        }
        .onFailure(::showError)
    }
  }

  fun deleteSmbServer(serverId: String) {
    val smb = smbRepository ?: return
    if (isBusy()) return
    viewModelScope.launch(Dispatchers.IO) {
      _state.update { it.copy(smbSettingsBusy = true) }
      runCatching { smb.deleteServer(serverId) }
        .onSuccess { loadSnapshot(message = "SMB設定と対象サーバ由来の蔵書を削除しました") }
        .onFailure(::showError)
    }
  }

  fun importAmazonLibraryJson(source: LibrarySource, json: String) {
    require(source == LibrarySource.KINDLE || source == LibrarySource.AUDIBLE)
    if (isBusy()) return
    viewModelScope.launch(Dispatchers.IO) {
      _state.update { it.copy(importingSource = source) }
      runCatching {
        val result = repository.importAmazonLibraryJson(source, json)
        val seriesMetadataFailed = if (repository is LibrarySeriesImportSupport) {
          try {
            repository.importSeriesMetadataJson(source, json)
            false
          } catch (error: CancellationException) {
            throw error
          } catch (_: Throwable) {
            runCatching { repository.clearSeriesMetadata(source) }
            true
          }
        } else {
          false
        }
        result to seriesMetadataFailed
      }
        .onSuccess { (result, seriesMetadataFailed) ->
          val warning = if (seriesMetadataFailed) {
            "（シリーズ情報は更新できませんでした）"
          } else {
            ""
          }
          loadSnapshot(
            message = "${source.label} から ${result.importedCount} 冊をインポートしました$warning",
          )
        }
        .onFailure(::showError)
    }
  }

  fun hideBook(book: LibraryBook) {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { repository.hideBook(book) }
        .onSuccess { loadSnapshot(message = "「${book.title}」を非表示にしました") }
        .onFailure(::showError)
    }
  }

  fun restoreBook(book: LibraryBook) {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { repository.restoreBook(book) }
        .onSuccess { loadSnapshot(message = "「${book.title}」を蔵書に戻しました") }
        .onFailure(::showError)
    }
  }

  fun setBookSeries(
    book: LibraryBook,
    seriesName: String,
    position: Int?,
  ) {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching {
        repository.setBookSeries(
          book = book,
          series = LibrarySeries(name = seriesName, position = position),
        )
      }
        .onSuccess { loadSnapshot(message = "「${book.title}」のシリーズを更新しました") }
        .onFailure(::showError)
    }
  }

  fun clearBookSeries(book: LibraryBook) {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { repository.clearBookSeries(book) }
        .onSuccess { loadSnapshot(message = "「${book.title}」をシリーズから外しました") }
        .onFailure(::showError)
    }
  }

  fun refresh() {
    viewModelScope.launch(Dispatchers.IO) { loadSnapshot() }
  }

  fun reportError(error: Throwable) {
    showError(error)
  }

  fun dismissMessage() {
    _state.update { it.copy(message = null) }
  }

  private fun isBusy(): Boolean = _state.value.let {
    it.syncing || it.importingSource != null || it.smbSyncing || it.smbSettingsBusy
  }

  private suspend fun loadSnapshot(message: String? = null) {
    runCatching {
      val snapshot = repository.snapshot()
      val servers = smbRepository?.servers().orEmpty()
      snapshot to servers
    }
      .onSuccess { (snapshot, servers) ->
        _state.update {
          it.copy(
            initialized = true,
            syncing = false,
            importingSource = null,
            smbSyncing = false,
            smbSettingsBusy = false,
            smbServers = servers,
            books = snapshot.books,
            hiddenBooks = snapshot.hiddenBooks,
            sourceStates = snapshot.sourceStates,
            message = message,
          )
        }
      }
      .onFailure(::showError)
  }

  private fun showError(error: Throwable) {
    _state.update {
      it.copy(
        initialized = true,
        syncing = false,
        importingSource = null,
        smbSyncing = false,
        smbSettingsBusy = false,
        message = error.message ?: "蔵書の操作に失敗しました",
      )
    }
  }

  class Factory(
    private val repository: LibraryRepository,
    private val smbRepository: SmbLibraryRepository? = null,
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      require(modelClass.isAssignableFrom(LibraryViewModel::class.java))
      @Suppress("UNCHECKED_CAST")
      return LibraryViewModel(repository, smbRepository) as T
    }
  }
}