package dev.terashima.yomitorirss.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiSettingsUiState(
  val supported: Boolean = false,
  val models: List<AiModelStatus> = emptyList(),
  val downloadProgress: AiModelDownloadProgress? = null,
  val summaryProgress: AiSummaryProgress? = null,
  val summaryPrompt: String = "",
  val inferenceBackend: AiInferenceBackend = AiInferenceBackend.CPU,
  val thinkingEnabled: Boolean = false,
  val message: String? = null,
)

class AiSettingsViewModel(
  private val repository: AiModelRepository,
) : ViewModel() {
  private val _state = MutableStateFlow(AiSettingsUiState(supported = repository.isSupported()))
  val state: StateFlow<AiSettingsUiState> = _state.asStateFlow()

  init {
    viewModelScope.launch {
      repository.models.collect { models -> _state.update { it.copy(models = models) } }
    }
    viewModelScope.launch {
      repository.downloadProgress.collect { progress -> _state.update { it.copy(downloadProgress = progress) } }
    }
    viewModelScope.launch {
      repository.summaryProgress.collect { progress -> _state.update { it.copy(summaryProgress = progress) } }
    }
    viewModelScope.launch {
      repository.summaryPrompt.collect { prompt -> _state.update { it.copy(summaryPrompt = prompt) } }
    }
    viewModelScope.launch {
      repository.inferenceSettings.collect { settings ->
        _state.update {
          it.copy(
            inferenceBackend = settings.backend,
            thinkingEnabled = settings.thinkingEnabled,
          )
        }
      }
    }
  }

  fun updateSummaryPrompt(prompt: String) {
    runCatching { repository.updateSummaryPrompt(prompt) }
      .onSuccess { _state.update { it.copy(message = "要約プロンプトを保存しました") } }
      .onFailure(::showError)
  }

  fun resetSummaryPrompt() {
    runCatching { repository.resetSummaryPrompt() }
      .onSuccess { _state.update { it.copy(message = "要約プロンプトを既定に戻しました") } }
      .onFailure(::showError)
  }

  fun setInferenceBackend(backend: AiInferenceBackend) {
    runCatching { repository.setInferenceBackend(backend) }.onFailure(::showError)
  }

  fun setThinkingEnabled(enabled: Boolean) {
    runCatching { repository.setThinkingEnabled(enabled) }.onFailure(::showError)
  }

  fun downloadModel(modelId: String) {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { repository.downloadModel(modelId) }
        .onSuccess { _state.update { it.copy(message = "モデルをダウンロードしました") } }
        .onFailure(::showError)
    }
  }

  fun selectModel(modelId: String) {
    runCatching { repository.selectModel(modelId) }.onFailure(::showError)
  }

  fun deleteModel(modelId: String) {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { repository.deleteModel(modelId) }
        .onSuccess { _state.update { it.copy(message = "モデルを削除しました") } }
        .onFailure(::showError)
    }
  }

  fun dismissMessage() {
    _state.update { it.copy(message = null) }
  }

  private fun showError(error: Throwable) {
    _state.update {
      it.copy(
        message = generateSequence(error) { cause -> cause.cause }
          .mapNotNull(Throwable::message)
          .firstOrNull(String::isNotBlank)
          ?: error.javaClass.simpleName,
      )
    }
  }

  class Factory(
    private val repository: AiModelRepository,
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      require(modelClass.isAssignableFrom(AiSettingsViewModel::class.java))
      @Suppress("UNCHECKED_CAST")
      return AiSettingsViewModel(repository) as T
    }
  }
}
