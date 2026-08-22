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
  val smbMetadataNormalizationBusy: Boolean = false,
  val smbServers: List<SmbServerSettings> = emptyList(),
  val smbCoverPrefetch: SmbCoverPrefetchSnapshot = SmbCoverPrefetchSnapshot(),
  val smbMetadataNormalization: SmbMetadataNormalizationBatchSnapshot? = null,
  val smbMetadataNormalizationPrompt: String = DEFAULT_SMB_METADATA_NORMALIZATION_PROMPT,
  val books: List<LibraryBook> = emptyList(),
  val hiddenBooks: List<LibraryBook> = emptyList(),
  val sourceStates: Map<LibrarySource, LibrarySourceState> = emptyMap(),
  val message: String? = null,
)

class LibraryViewModel(
  private val repository: LibraryRepository,
  private val smbRepository: SmbLibraryRepository? = null,
  private val smbCoverPrefetchScheduler: SmbCoverPrefetchScheduler? = null,
  private val smbMetadataNormalizationRepository: SmbMetadataNormalizationRepository? = null,
  private val smbMetadataNormalizationScheduler: SmbMetadataNormalizationScheduler? = null,
  private val smbMetadataNormalizationPromptRepository: SmbMetadataNormalizationPromptRepository? = null,
) : ViewModel() {
  private val _state = MutableStateFlow(LibraryUiState())
  val state: StateFlow<LibraryUiState> = _state.asStateFlow()
  private var coverQueuePollingJob: Job? = null
  private var normalizationPollingJob: Job? = null
  private var coverQueueSchedulingEnsured = false

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
          enqueueCoverPrefetch()
          smbMetadataNormalizationScheduler?.kick()
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
            enqueueCoverPrefetch()
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
          if (count > 0) enqueueCoverPrefetch()
          smbMetadataNormalizationScheduler?.kick()
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

  fun rescheduleSmbCoverPrefetch() {
    val scheduler = smbCoverPrefetchScheduler ?: return
    val snapshot = _state.value.smbCoverPrefetch
    if (
      _state.value.smbCoverPrefetchBusy ||
      snapshot.pendingCount <= 0 ||
      snapshot.runtime.state != SmbCoverPrefetchWorkerState.ENQUEUED ||
      snapshot.runtime.waitReason != SmbCoverPrefetchWaitReason.SCHEDULER
    ) {
      return
    }
    viewModelScope.launch(Dispatchers.IO) {
      _state.update { it.copy(smbCoverPrefetchBusy = true) }
      runCatching { scheduler.reschedule() }
        .onSuccess {
          coverQueueSchedulingEnsured = true
          loadSnapshot(message = "WorkManagerへ表紙先読みの実行を再要求しました")
        }
        .onFailure(::showError)
    }
  }

  fun startSmbMetadataNormalization() {
    val normalizer = smbMetadataNormalizationRepository ?: return
    val smb = smbRepository ?: return
    if (isBusy()) return
    viewModelScope.launch(Dispatchers.IO) {
      _state.update { it.copy(smbMetadataNormalizationBusy = true) }
      runCatching {
        val books = _state.value.let { it.books + it.hiddenBooks }
        val count = normalizer.startBatch(books)
        val coverCount = smb.enqueueMissingCoverPrefetch()
        if (coverCount > 0 || smb.coverPrefetchSnapshot().hasActiveWork) enqueueCoverPrefetch()
        smbMetadataNormalizationScheduler?.kick()
        count
      }
        .onSuccess { count ->
          loadSnapshot(message = "ファイルサーバ書籍 $count 冊の書誌解析を開始しました")
        }
        .onFailure(::showError)
    }
  }

  fun saveSmbMetadataNormalizationPrompt(prompt: String) {
    val prompts = smbMetadataNormalizationPromptRepository ?: return
    if (_state.value.smbMetadataNormalizationBusy) return
    viewModelScope.launch(Dispatchers.IO) {
      _state.update { it.copy(smbMetadataNormalizationBusy = true) }
      runCatching { prompts.update(prompt) }
        .onSuccess { loadSnapshot(message = "書誌正規化プロンプトを保存しました") }
        .onFailure(::showError)
    }
  }

  fun resetSmbMetadataNormalizationPrompt() {
    val prompts = smbMetadataNormalizationPromptRepository ?: return
    if (_state.value.smbMetadataNormalizationBusy) return
    viewModelScope.launch(Dispatchers.IO) {
      _state.update { it.copy(smbMetadataNormalizationBusy = true) }
      runCatching { prompts.reset() }
        .onSuccess { loadSnapshot(message = "書誌正規化プロンプトを既定値へ戻しました") }
        .onFailure(::showError)
    }
  }

  fun applySmbMetadataCandidate(
    sourceId: String,
    proposedFileName: String,
    proposal: SmbBookMetadataProposal,
  ) {
    val normalizer = smbMetadataNormalizationRepository ?: return
    if (_state.value.smbMetadataNormalizationBusy) return
    viewModelScope.launch(Dispatchers.IO) {
      _state.update { it.copy(smbMetadataNormalizationBusy = true) }
      runCatching { normalizer.applyCandidate(sourceId, proposedFileName, proposal) }
        .onSuccess {
          enqueueCoverPrefetch()
          loadSnapshot(message = "書誌情報とファイル名を反映しました")
        }
        .onFailure(::showError)
    }
  }

  fun deferSmbMetadataCandidate(sourceId: String) = updateNormalizationCandidate(
    sourceId = sourceId,
    message = "書誌候補を保留しました",
  ) { it.deferCandidate(sourceId) }

  fun rejectSmbMetadataCandidate(sourceId: String) = updateNormalizationCandidate(
    sourceId = sourceId,
    message = "書誌候補を却下して確定しました",
  ) { it.rejectCandidate(sourceId) }

  fun reopenSmbMetadataCandidate(sourceId: String) = updateNormalizationCandidate(
    sourceId = sourceId,
    message = "書誌候補を未確認へ戻しました",
  ) { it.reopenCandidate(sourceId) }

  fun retrySmbMetadataCandidate(sourceId: String) {
    val normalizer = smbMetadataNormalizationRepository ?: return
    val smb = smbRepository ?: return
    if (_state.value.smbMetadataNormalizationBusy) return
    viewModelScope.launch(Dispatchers.IO) {
      _state.update { it.copy(smbMetadataNormalizationBusy = true) }
      runCatching {
        normalizer.retryCandidate(sourceId)
        val failedCoverCount = smb.retryFailedCoverPrefetch()
        val missingCoverCount = smb.enqueueMissingCoverPrefetch()
        if (
          failedCoverCount > 0 ||
          missingCoverCount > 0 ||
          smb.coverPrefetchSnapshot().hasActiveWork
        ) {
          enqueueCoverPrefetch()
        }
        smbMetadataNormalizationScheduler?.kick()
      }
        .onSuccess { loadSnapshot(message = "書誌候補を再解析します") }
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
          enqueueCoverPrefetch()
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

  fun mergeSeries(updates: List<LibraryBookSeriesUpdate>) {
    if (updates.isEmpty()) return
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { repository.setBookSeries(updates) }
        .onSuccess {
          val seriesName = updates.first().series.name
          loadSnapshot(message = "シリーズを「$seriesName」にマージしました")
        }
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

  private fun updateNormalizationCandidate(
    sourceId: String,
    message: String,
    action: suspend (SmbMetadataNormalizationRepository) -> Unit,
  ) {
    val normalizer = smbMetadataNormalizationRepository ?: return
    if (_state.value.smbMetadataNormalizationBusy) return
    viewModelScope.launch(Dispatchers.IO) {
      _state.update { it.copy(smbMetadataNormalizationBusy = true) }
      runCatching { action(normalizer) }
        .onSuccess { loadSnapshot(message = message) }
        .onFailure(::showError)
    }
  }

  private fun isBusy(): Boolean = _state.value.let {
    it.syncing || it.importingSource != null || it.smbSyncing || it.smbSettingsBusy ||
      it.smbBookActionBusy || it.smbMetadataNormalizationBusy
  }

  private suspend fun loadSnapshot(message: String? = null) {
    runCatching {
      LoadedLibraryState(
        snapshot = repository.snapshot(),
        servers = smbRepository?.servers().orEmpty(),
        coverPrefetch = smbRepository?.coverPrefetchSnapshot() ?: SmbCoverPrefetchSnapshot(),
        normalization = smbMetadataNormalizationRepository?.batchSnapshot(),
        normalizationPrompt = smbMetadataNormalizationPromptRepository?.prompt()
          ?: DEFAULT_SMB_METADATA_NORMALIZATION_PROMPT,
      )
    }
      .onSuccess { loaded ->
        _state.update {
          it.copy(
            initialized = true,
            syncing = false,
            importingSource = null,
            smbSyncing = false,
            smbSettingsBusy = false,
            smbBookActionBusy = false,
            smbCoverPrefetchBusy = false,
            smbMetadataNormalizationBusy = false,
            smbServers = loaded.servers,
            smbCoverPrefetch = loaded.coverPrefetch,
            smbMetadataNormalization = loaded.normalization,
            smbMetadataNormalizationPrompt = loaded.normalizationPrompt,
            books = loaded.snapshot.books,
            hiddenBooks = loaded.snapshot.hiddenBooks,
            sourceStates = loaded.snapshot.sourceStates,
            message = message,
          )
        }
        ensureCoverPrefetchScheduled(loaded.coverPrefetch)
        ensureCoverQueuePolling(loaded.coverPrefetch)
        ensureNormalizationPolling(loaded.normalization)
      }
      .onFailure(::showError)
  }

  private fun enqueueCoverPrefetch() {
    smbCoverPrefetchScheduler?.enqueue()
    coverQueueSchedulingEnsured = true
  }

  private fun ensureCoverPrefetchScheduled(snapshot: SmbCoverPrefetchSnapshot) {
    if (!snapshot.hasActiveWork || coverQueueSchedulingEnsured) return
    if (runCatching { smbCoverPrefetchScheduler?.enqueue() }.isSuccess) {
      coverQueueSchedulingEnsured = true
    }
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
        delay(POLL_INTERVAL_MILLIS)
        val latest = runCatching { smb.coverPrefetchSnapshot() }.getOrNull() ?: continue
        _state.update { it.copy(smbCoverPrefetch = latest) }
        if (!latest.hasActiveWork) {
          smbMetadataNormalizationScheduler?.kick()
          loadSnapshot()
          break
        }
      }
    }
  }

  private fun ensureNormalizationPolling(snapshot: SmbMetadataNormalizationBatchSnapshot?) {
    if (snapshot?.hasActiveWork != true) {
      normalizationPollingJob?.cancel()
      normalizationPollingJob = null
      return
    }
    if (normalizationPollingJob?.isActive == true) return
    val normalizer = smbMetadataNormalizationRepository ?: return
    normalizationPollingJob = viewModelScope.launch(Dispatchers.IO) {
      while (isActive) {
        delay(POLL_INTERVAL_MILLIS)
        val latest = runCatching { normalizer.batchSnapshot() }.getOrNull() ?: continue
        _state.update { it.copy(smbMetadataNormalization = latest) }
        if (latest?.hasActiveWork != true) {
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
        smbMetadataNormalizationBusy = false,
        message = error.message ?: "蔵書の操作に失敗しました",
      )
    }
  }

  class Factory(
    private val repository: LibraryRepository,
    private val smbRepository: SmbLibraryRepository? = null,
    private val smbCoverPrefetchScheduler: SmbCoverPrefetchScheduler? = null,
    private val smbMetadataNormalizationRepository: SmbMetadataNormalizationRepository? = null,
    private val smbMetadataNormalizationScheduler: SmbMetadataNormalizationScheduler? = null,
    private val smbMetadataNormalizationPromptRepository: SmbMetadataNormalizationPromptRepository? = null,
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      require(modelClass.isAssignableFrom(LibraryViewModel::class.java))
      @Suppress("UNCHECKED_CAST")
      return LibraryViewModel(
        repository = repository,
        smbRepository = smbRepository,
        smbCoverPrefetchScheduler = smbCoverPrefetchScheduler,
        smbMetadataNormalizationRepository = smbMetadataNormalizationRepository,
        smbMetadataNormalizationScheduler = smbMetadataNormalizationScheduler,
        smbMetadataNormalizationPromptRepository = smbMetadataNormalizationPromptRepository,
      ) as T
    }
  }

  private data class LoadedLibraryState(
    val snapshot: LibrarySnapshot,
    val servers: List<SmbServerSettings>,
    val coverPrefetch: SmbCoverPrefetchSnapshot,
    val normalization: SmbMetadataNormalizationBatchSnapshot?,
    val normalizationPrompt: String,
  )

  private companion object {
    const val POLL_INTERVAL_MILLIS = 2_000L
  }
}
