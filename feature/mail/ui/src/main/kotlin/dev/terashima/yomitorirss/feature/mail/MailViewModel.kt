package dev.terashima.yomitorirss.feature.mail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MailUiState(
  val initialized: Boolean = false,
  val loading: Boolean = false,
  val accounts: List<MailAccount> = emptyList(),
  val selectedAccountId: String? = null,
  val mailbox: Mailbox = Mailbox.UNREAD,
  val query: String = "",
  val threads: List<MailThread> = emptyList(),
  val selectedThread: MailThread? = null,
  val labels: List<MailLabel> = emptyList(),
  val message: String? = null,
)

class MailViewModel(
  private val repository: MailRepository,
) : ViewModel() {
  private val _state = MutableStateFlow(MailUiState())
  val state: StateFlow<MailUiState> = _state.asStateFlow()
  private var syncPollingJob: Job? = null

  init {
    reload()
  }

  fun connectAuthorizedAccount(email: String, displayName: String?, accessToken: String) {
    mutate(
      action = { repository.connectAccount(email, displayName, accessToken) },
      successMessage = "$email を追加しました。メールはバックグラウンドで同期します",
    )
  }

  fun removeSelectedAccount() {
    val accountId = _state.value.selectedAccountId ?: return
    mutate(
      action = { repository.removeAccount(accountId) },
      successMessage = "メールアカウントを削除しました",
    )
    _state.update { it.copy(selectedAccountId = null, selectedThread = null) }
  }

  fun selectAccount(accountId: String?) {
    _state.update { it.copy(selectedAccountId = accountId, selectedThread = null) }
    reload()
  }

  fun selectMailbox(mailbox: Mailbox) {
    _state.update { it.copy(mailbox = mailbox, selectedThread = null) }
    reload()
  }

  fun updateQuery(query: String) {
    _state.update { it.copy(query = query) }
  }

  fun search() = reload()

  fun refresh() {
    viewModelScope.launch(Dispatchers.IO) {
      _state.update { it.copy(loading = true) }
      runCatching { repository.sync(_state.value.selectedAccountId) }
        .onSuccess { loadOverview() }
        .onFailure(::showError)
    }
  }

  fun openThread(thread: MailThread) {
    viewModelScope.launch(Dispatchers.IO) {
      _state.update { it.copy(loading = true) }
      runCatching {
        val detail = repository.getThread(thread.accountId, thread.id)
        val labels = repository.getLabels(thread.accountId)
        detail to labels
      }.onSuccess { (detail, labels) ->
        _state.update { it.copy(loading = false, selectedThread = detail, labels = labels) }
      }.onFailure(::showError)
    }
  }

  fun closeThread() {
    _state.update { it.copy(selectedThread = null, labels = emptyList()) }
  }

  fun toggleRead(thread: MailThread) = mutateThread(
    thread = thread,
    hideFromCurrentList = _state.value.mailbox == Mailbox.UNREAD && thread.isUnread,
  ) {
    repository.setThreadRead(thread.accountId, thread.id, read = thread.isUnread)
  }

  fun toggleStarred(thread: MailThread) {
    val mailbox = _state.value.mailbox
    val starAndRead = mailbox == Mailbox.UNREAD && !thread.isStarred
    mutateThread(
      thread = thread,
      hideFromCurrentList = starAndRead || (mailbox == Mailbox.STARRED && thread.isStarred),
    ) {
      repository.setThreadStarred(thread.accountId, thread.id, starred = !thread.isStarred)
      if (starAndRead) repository.setThreadRead(thread.accountId, thread.id, read = true)
    }
  }

  fun archive(thread: MailThread) = mutateThread(
    thread = thread,
    hideFromCurrentList = _state.value.mailbox == Mailbox.UNREAD || _state.value.mailbox == Mailbox.INBOX,
  ) {
    repository.archiveThread(thread.accountId, thread.id)
  }

  fun trash(thread: MailThread) = mutateThread(
    thread = thread,
    hideFromCurrentList = true,
  ) {
    repository.trashThread(thread.accountId, thread.id)
  }

  fun applyLabel(thread: MailThread, labelId: String) = mutateThread(
    thread = thread,
    hideFromCurrentList = labelId == "INBOX" && _state.value.mailbox == Mailbox.ALL_MAIL,
  ) {
    repository.applyLabel(thread.accountId, thread.id, labelId)
  }

  fun dismissMessage() {
    _state.update { it.copy(message = null) }
  }

  private fun reload(showLoading: Boolean = true) {
    viewModelScope.launch(Dispatchers.IO) { loadOverview(showLoading) }
  }

  private suspend fun loadOverview(showLoading: Boolean = true) {
    if (showLoading) _state.update { it.copy(loading = true) }
    runCatching {
      var accounts = repository.getAccounts()
      val pendingInitialSyncs = accounts.filter { account ->
        account.lastSyncedAtEpochMillis == null && account.syncState == MailSyncState.IDLE
      }
      pendingInitialSyncs.forEach { account -> repository.sync(account.id) }
      if (pendingInitialSyncs.isNotEmpty()) accounts = repository.getAccounts()

      val selected = _state.value.selectedAccountId?.takeIf { id -> accounts.any { it.id == id } }
      val mailbox = _state.value.mailbox
      val threads = repository.getThreads(selected, mailbox, _state.value.query).forMailbox(mailbox)
      Triple(accounts, selected, threads)
    }.onSuccess { (accounts, selected, threads) ->
      _state.update {
        it.copy(
          initialized = true,
          loading = if (showLoading) false else it.loading,
          accounts = accounts,
          selectedAccountId = selected,
          threads = threads,
        )
      }
      ensureSyncPolling(accounts)
    }.onFailure(::showError)
  }

  private fun ensureSyncPolling(accounts: List<MailAccount>) {
    val active = accounts.any { account ->
      account.syncState == MailSyncState.SYNCING || account.syncState == MailSyncState.WAITING_FOR_NETWORK
    }
    if (!active || syncPollingJob?.isActive == true) return
    syncPollingJob = viewModelScope.launch(Dispatchers.IO) {
      try {
        while (true) {
          delay(SYNC_POLL_INTERVAL_MILLIS)
          loadOverview(showLoading = false)
          val stillActive = _state.value.accounts.any { account ->
            account.syncState == MailSyncState.SYNCING || account.syncState == MailSyncState.WAITING_FOR_NETWORK
          }
          if (!stillActive) break
        }
      } finally {
        syncPollingJob = null
      }
    }
  }

  private fun mutate(action: suspend () -> Unit, successMessage: String? = null) {
    viewModelScope.launch(Dispatchers.IO) {
      _state.update { it.copy(loading = true) }
      runCatching { action() }
        .onSuccess {
          successMessage?.let { message -> _state.update { state -> state.copy(message = message) } }
          loadOverview()
        }
        .onFailure(::showError)
    }
  }

  private fun mutateThread(
    thread: MailThread,
    hideFromCurrentList: Boolean = false,
    action: suspend () -> Unit,
  ) {
    if (hideFromCurrentList) {
      _state.update { state ->
        state.copy(threads = state.threads.filterNot { it.id == thread.id && it.accountId == thread.accountId })
      }
    }
    viewModelScope.launch(Dispatchers.IO) {
      _state.update { it.copy(loading = true) }
      runCatching { action() }
        .onSuccess {
          val selected = _state.value.selectedThread
          loadOverview()
          if (selected?.id == thread.id && selected.accountId == thread.accountId) {
            runCatching { repository.getThread(thread.accountId, thread.id) }
              .onSuccess { detail -> _state.update { it.copy(selectedThread = detail) } }
              .onFailure { _state.update { it.copy(selectedThread = null) } }
          }
        }
        .onFailure { error ->
          if (hideFromCurrentList) loadOverview(showLoading = false)
          showError(error)
        }
    }
  }

  private fun showError(error: Throwable) {
    _state.update {
      it.copy(
        initialized = true,
        loading = false,
        message = error.message ?: "メールの操作に失敗しました",
      )
    }
  }

  class Factory(private val repository: MailRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      require(modelClass.isAssignableFrom(MailViewModel::class.java))
      @Suppress("UNCHECKED_CAST")
      return MailViewModel(repository) as T
    }
  }

  private companion object {
    const val SYNC_POLL_INTERVAL_MILLIS = 2_000L
  }
}

internal fun List<MailThread>.forMailbox(mailbox: Mailbox): List<MailThread> = when (mailbox) {
  Mailbox.UNREAD -> filter(MailThread::isInInbox)
  Mailbox.ALL_MAIL -> filterNot(MailThread::isInInbox)
  Mailbox.INBOX,
  Mailbox.STARRED -> this
}
