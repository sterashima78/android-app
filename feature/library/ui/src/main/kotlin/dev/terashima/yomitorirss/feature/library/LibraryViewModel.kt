package dev.terashima.yomitorirss.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class LibraryUiState(
  val initialized: Boolean = false,
  val syncing: Boolean = false,
  val importingSource: LibrarySource? = null,
  val smbSyncing: Boolean = false,
  val smbSettingsBusy: Boolean = false,
  val smbBookActionBusy: Boolean = false,
  val smbCoverPrefetchBusy: Boolean = false,
  val smbServers: List<SmbServerSettings> = emptyList(),
  val smbCoverPrefetch: SmbCoverPrefetchSnapshot = SmbCoverPrefetchSnapshot(),
  val books: List<LibraryBook> = emptyList(),
  val hiddenBooks: List<LibraryBook> = emptyList(),
  val sourceStates: Map<LibrarySource, LibrarySourceState> = emptyMap(),
  val message: String? = null,
)

class LibraryViewModel(
  private val repository: LibraryRepository,
  private val smbRepository: SmbLibraryRepository? = null,
  private val smbCoverPrefetchScheduler: SmbCoverPrefetchScheduler? = null,
) : ViewModel() {
  private val _state = MutableStateFlow(LibraryUiState())
  val state: StateFlow<LibraryUiState> = _state.asStateFlow()
  private var coverQueuePollingJob: Job? = null

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
          smbCoverPrefetchScheduler?.kick()
          loadSnapshot(message = "ファイルサーバから ${result.importedCount} 冊を同期しました")
        }
        .onFailure(::showError)
    }
  }

  fun enqueueMissingSmbCovers() {
    val smb = smbRepository ?: return
    if (_state.value.smbCoverPrefetchBusy) return
    viewModelScope.launch(Dispatchers.IO) {
      _state.update { it.copy(smbCoverPrefetchBusy = true) }
      runCatching { smb.enqueueMissingCoverPrefetch() }
        .onSuccess { count ->
          if (count > 0 || smb.coverPrefetchSnapshot().hasActiveWork) {
            smbCoverPrefetchScheduler?.kick()
          }
          loadSnapshot(
            message = if (count > 0) {
              "表紙先読みを $count 冊キューに追加しました"
            } else {
              "新しく追加する表紙先読みはありません"
            },
          )
        }
        .onFailure(::showError)
    }
  }

  fun retryFailedSmbCovers() {
    val smb = smbRepository ?: return
    if (_state.value.smbCoverPrefetchBusy) return
    viewModelScope.launch(Dispatchers.IO) {
      _state.update { it.copy(smbCoverPrefetchBusy = true) }
      runCatching { smb.retryFailedCoverPrefetch() }
        .onSuccess { count ->
          if (count > 0) smbCoverPrefetchScheduler?.kick()
          loadSnapshot(
            message = if (count > 0) {
              "失敗した表紙先読み $count 冊を再試行します"
            } else {
              "再試行する失敗ジョブはありません"
            },
          )
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

  fun renameSmbBook(book: LibraryBook, newFileName: String) {
    val smb = smbRepository ?: return
    if (isBusy()) return
    viewModelScope.launch(Dispatchers.IO) {
      _state.update { it.copy(smbBookActionBusy = true) }
      runCatching { smb.renameBook(book, newFileName) }
        .onSuccess { renamed ->
          smbCoverPrefetchScheduler?.kick()
          loadSnapshot(message = "「${book.title}」を「${renamed.title}」へ変更しました")
        }
        .onFailure(::showError)
    }
  }

  fun deleteSmbBook(book: LibraryBook) {
    val smb = smbRepository ?: return
    if (isBusy()) return
    viewModelScope.launch(Dispatchers.IO) {
      _state.update { it.copy(smbBookActionBusy = true) }
      runCatching { smb.deleteBook(book) }
        .onSuccess { loadSnapshot(message = "「${book.title}」をファイルサーバから削除しました") }
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
    it.syncing || it.importingSource != null || it.smbSyncing || it.smbSettingsBusy || it.smbBookActionBusy
  }

  private suspend fun loadSnapshot(message: String? = null) {
    runCatching {
      val snapshot = repository.snapshot()
      val servers = smbRepository?.servers().orEmpty()
      val coverPrefetch = smbRepository?.coverPrefetchSnapshot() ?: SmbCoverPrefetchSnapshot()
      Triple(snapshot, servers, coverPrefetch)
    }
      .onSuccess { (snapshot, servers, coverPrefetch) ->
        _state.update {
          it.copy(
            initialized = true,
            syncing = false,
            importingSource = null,
            smbSyncing = false,
            smbSettingsBusy = false,
            smbBookActionBusy = false,
            smbCoverPrefetchBusy = false,
            smbServers = servers,
            smbCoverPrefetch = coverPrefetch,
            books = snapshot.books,
            hiddenBooks = snapshot.hiddenBooks,
            sourceStates = snapshot.sourceStates,
            message = message,
          )
        }
        ensureCoverQueuePolling(coverPrefetch)
      }
      .onFailure(::showError)
  }

  private fun ensureCoverQueuePolling(snapshot: SmbCoverPrefetchSnapshot) {
    if (!snapshot.hasActiveWork) {
      coverQueuePollingJob?.cancel()
      coverQueuePollingJob = null
      return
    }
    if (coverQueuePollingJob?.isActive == true) return
    val smb = smbRepository ?: return
    coverQueuePollingJob = viewModelScope.launch(Dispatchers.IO) {
      while (isActive) {
        delay(COVER_QUEUE_POLL_INTERVAL_MILLIS)
        val latest = runCatching { smb.coverPrefetchSnapshot() }.getOrNull() ?: continue
        _state.update { it.copy(smbCoverPrefetch = latest) }
        if (!latest.hasActiveWork) {
          loadSnapshot()
          break
        }
      }
    }
  }

  private fun showError(error: Throwable) {
    _state.update {
      it.copy(
        initialized = true,
        syncing = false,
        importingSource = null,
        smbSyncing = false,
        smbSettingsBusy = false,
        smbBookActionBusy = false,
        smbCoverPrefetchBusy = false,
        message = error.message ?: "蔵書の操作に失敗しました",
      )
    }
  }

  class Factory(
    private val repository: LibraryRepository,
    private val smbRepository: SmbLibraryRepository? = null,
    private val smbCoverPrefetchScheduler: SmbCoverPrefetchScheduler? = null,
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      require(modelClass.isAssignableFrom(LibraryViewModel::class.java))
      @Suppress("UNCHECKED_CAST")
      return LibraryViewModel(repository, smbRepository, smbCoverPrefetchScheduler) as T
    }
  }

  private companion object {
    const val COVER_QUEUE_POLL_INTERVAL_MILLIS = 2_000L
  }
}
