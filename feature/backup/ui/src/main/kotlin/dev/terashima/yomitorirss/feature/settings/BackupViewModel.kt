package dev.terashima.yomitorirss.feature.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackupUiState(
  val configured: Boolean = false,
  val folderUri: String? = null,
  val folderName: String? = null,
  val lastSuccessAt: String? = null,
  val lastFileName: String? = null,
  val lastError: String? = null,
  val running: Boolean = false,
  val message: String? = null,
  val restoreCompleted: Boolean = false,
)

class BackupViewModel(
  private val repository: BackupRepository,
) : ViewModel() {
  private val _state = MutableStateFlow(BackupUiState())
  val state: StateFlow<BackupUiState> = _state.asStateFlow()

  init {
    runCatching {
      repository.ensureScheduled()
      refreshStatus()
    }.onFailure(::showError)
  }

  fun refreshStatus() {
    updateStatus(running = _state.value.running)
  }

  fun exportBackup(documentUri: String) {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { repository.exportTo(documentUri) }
        .onSuccess { _state.update { it.copy(message = "バックアップを保存しました") } }
        .onFailure(::showError)
    }
  }

  fun importBackup(documentUri: String) {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { repository.restoreFrom(documentUri) }
        .onSuccess {
          updateStatus(
            running = false,
            message = "バックアップから復元しました",
            restoreCompleted = true,
          )
        }
        .onFailure(::showError)
    }
  }

  fun configureGoogleDrive(folderUri: String) {
    _state.update { it.copy(running = true) }
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { repository.configureGoogleDrive(folderUri) }
        .onSuccess { result ->
          val message = when (result) {
            ConfigureGoogleDriveResult.Enabled -> "Google Driveへの自動バックアップを有効にしました"
            is ConfigureGoogleDriveResult.EnabledWithInitialBackupFailure ->
              "保存先を設定しましたが、初回バックアップに失敗しました: ${result.message}"
          }
          updateStatus(running = false, message = message)
        }
        .onFailure { error ->
          updateStatus(
            running = false,
            message = "Google Driveの保存先を設定できませんでした: ${error.userMessage()}",
          )
        }
    }
  }

  fun backupToGoogleDriveNow() {
    if (!_state.value.configured) {
      _state.update { it.copy(message = "Google Driveの保存先を設定してください") }
      return
    }
    _state.update { it.copy(running = true) }
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { repository.backupToGoogleDriveNow() }
        .onSuccess { fileName ->
          updateStatus(running = false, message = "$fileName をGoogle Driveに保存しました")
        }
        .onFailure { error ->
          updateStatus(
            running = false,
            message = "Google Driveへのバックアップに失敗しました: ${error.userMessage()}",
          )
        }
    }
  }

  fun disableGoogleDrive() {
    runCatching { repository.disableGoogleDrive() }
      .onSuccess {
        updateStatus(running = false, message = "Google Driveへの自動バックアップを無効にしました")
      }
      .onFailure(::showError)
  }

  fun dismissMessage() {
    _state.update { it.copy(message = null) }
  }

  fun consumeRestoreCompleted() {
    _state.update { it.copy(restoreCompleted = false) }
  }

  private fun updateStatus(
    running: Boolean,
    message: String? = null,
    restoreCompleted: Boolean = _state.value.restoreCompleted,
  ) {
    val status = repository.status()
    _state.update {
      it.copy(
        configured = status.configured,
        folderUri = status.folderUri,
        folderName = status.folderName,
        lastSuccessAt = status.lastSuccessAt,
        lastFileName = status.lastFileName,
        lastError = status.lastError,
        running = running,
        message = message ?: it.message,
        restoreCompleted = restoreCompleted,
      )
    }
  }

  private fun showError(error: Throwable) {
    _state.update { it.copy(running = false, message = error.userMessage()) }
  }

  class Factory(
    private val repository: BackupRepository,
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      require(modelClass.isAssignableFrom(BackupViewModel::class.java)) {
        "Unknown ViewModel class: ${modelClass.name}"
      }
      @Suppress("UNCHECKED_CAST")
      return BackupViewModel(repository) as T
    }
  }
}

private fun Throwable.userMessage(): String =
  generateSequence(this) { it.cause }
    .mapNotNull(Throwable::message)
    .firstOrNull(String::isNotBlank)
    ?: javaClass.simpleName
