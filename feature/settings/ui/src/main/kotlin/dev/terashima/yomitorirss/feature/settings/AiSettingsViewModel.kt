package dev.terashima.yomitorirss.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.terashima.yomitorirss.feature.summary.SummaryExecutionProvider
import dev.terashima.yomitorirss.feature.summary.SummaryExecutionSettings
import dev.terashima.yomitorirss.feature.summary.SummaryPromptSettings
import kotlinx.coroutines.CancellationException
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
  val contextSizeMode: AiContextSizeMode = AiContextSizeMode.AUTO,
  val thinkingEnabled: Boolean = false,
  val speculativeDecodingEnabled: Boolean = false,
  val benchmarkRunning: Boolean = false,
  val benchmarkResult: AiModelBenchmarkComparison? = null,
  val benchmarkError: String? = null,
  val contextBenchmarkResult: AiContextBenchmarkReport? = null,
  val contextBenchmarkError: String? = null,
  val chatGptConnected: Boolean = false,
  val chatGptAccountLabel: String? = null,
  val chatGptExpiresAtEpochMillis: Long? = null,
  val chatGptLogin: ChatGptDebugLoginSession? = null,
  val chatGptModels: List<ChatGptProviderModel> = emptyList(),
  val chatGptSelectedModelId: String? = null,
  val chatGptModelsLoading: Boolean = false,
  val summaryExecutionProvider: SummaryExecutionProvider = SummaryExecutionProvider.LOCAL,
  val chatGptModelId: String = "",
  val chatGptPrompt: String = "接続確認とだけ返してください。",
  val chatGptResponse: String? = null,
  val chatGptElapsedMillis: Long? = null,
  val chatGptBusy: Boolean = false,
  val chatGptStatusMessage: String? = null,
  val chatGptError: String? = null,
  val message: String? = null,
)

private data class ContextBenchmarkKey(
  val modelId: String?,
  val backend: AiInferenceBackend,
  val speculativeDecodingEnabled: Boolean,
)

class AiSettingsViewModel(
  private val repository: AiModelRepository,
  private val summaryPromptSettings: SummaryPromptSettings,
  private val chatGptDebugRepository: ChatGptDebugRepository,
  private val chatGptProviderRepository: ChatGptProviderRepository,
  private val summaryExecutionSettings: SummaryExecutionSettings,
) : ViewModel() {
  private val initialChatGptModelId = chatGptProviderRepository.selectedModelId()
    ?: chatGptDebugRepository.defaultModelId
  private val _state = MutableStateFlow(
    AiSettingsUiState(
      supported = repository.isSupported(),
      chatGptSelectedModelId = chatGptProviderRepository.selectedModelId(),
      chatGptModelId = initialChatGptModelId,
      summaryExecutionProvider = summaryExecutionSettings.currentProvider(),
    ),
  )
  val state: StateFlow<AiSettingsUiState> = _state.asStateFlow()

  private var lastContextBenchmarkKey: ContextBenchmarkKey? = null

  init {
    viewModelScope.launch {
      repository.models.collect { models ->
        _state.update { it.copy(models = models) }
        refreshContextBenchmarkIfNeeded()
      }
    }
    viewModelScope.launch {
      repository.downloadProgress.collect { progress ->
        _state.update { it.copy(downloadProgress = progress?.takeIf { value -> value.isActive }) }
      }
    }
    viewModelScope.launch {
      repository.summaryProgress.collect { progress -> _state.update { it.copy(summaryProgress = progress) } }
    }
    viewModelScope.launch {
      summaryPromptSettings.prompt.collect { prompt -> _state.update { it.copy(summaryPrompt = prompt) } }
    }
    viewModelScope.launch {
      summaryExecutionSettings.provider.collect { provider ->
        _state.update { it.copy(summaryExecutionProvider = provider) }
      }
    }
    viewModelScope.launch {
      repository.inferenceSettings.collect { settings ->
        _state.update {
          it.copy(
            inferenceBackend = settings.backend,
            contextSizeMode = settings.contextSizeMode,
            thinkingEnabled = settings.thinkingEnabled,
            speculativeDecodingEnabled = settings.speculativeDecodingEnabled,
          )
        }
        refreshContextBenchmarkIfNeeded()
      }
    }
  }

  fun prepareModelManager() {
    if (!_state.value.benchmarkRunning) clearBenchmark()
  }

  fun prepareChatGptDebug() {
    refreshChatGptStatus(clearTransientState = true)
    if (_state.value.chatGptConnected) refreshChatGptModels()
  }

  fun startChatGptLogin() {
    if (_state.value.chatGptBusy) return
    _state.update {
      it.copy(
        chatGptBusy = true,
        chatGptLogin = null,
        chatGptStatusMessage = null,
        chatGptError = null,
        chatGptResponse = null,
        chatGptElapsedMillis = null,
      )
    }
    viewModelScope.launch {
      try {
        val login = chatGptDebugRepository.startLogin()
        _state.update {
          it.copy(
            chatGptLogin = login,
            chatGptStatusMessage = "ブラウザで認証後、認証完了を確認してください。",
          )
        }
      } catch (error: CancellationException) {
        throw error
      } catch (error: Throwable) {
        showChatGptError(error)
      } finally {
        _state.update { it.copy(chatGptBusy = false) }
      }
    }
  }

  fun pollChatGptLogin() {
    if (_state.value.chatGptBusy) return
    val login = _state.value.chatGptLogin ?: return
    _state.update { it.copy(chatGptBusy = true, chatGptStatusMessage = null, chatGptError = null) }
    viewModelScope.launch {
      try {
        when (chatGptDebugRepository.pollLogin(login.id)) {
          ChatGptDebugLoginPollResult.PENDING -> {
            _state.update { it.copy(chatGptStatusMessage = "まだ認証待ちです。") }
          }
          ChatGptDebugLoginPollResult.SLOW_DOWN -> {
            _state.update {
              it.copy(chatGptStatusMessage = "確認間隔が短すぎます。数秒待ってから再確認してください。")
            }
          }
          ChatGptDebugLoginPollResult.AUTHORIZED -> {
            refreshChatGptStatus(clearTransientState = true)
            _state.update { it.copy(chatGptStatusMessage = "ChatGPTに接続しました。") }
            refreshChatGptModels()
          }
        }
      } catch (error: CancellationException) {
        throw error
      } catch (error: Throwable) {
        showChatGptError(error)
      } finally {
        _state.update { it.copy(chatGptBusy = false) }
      }
    }
  }

  fun logoutChatGpt() {
    runCatching { chatGptDebugRepository.logout() }
      .onSuccess {
        _state.update {
          it.copy(
            chatGptConnected = false,
            chatGptAccountLabel = null,
            chatGptExpiresAtEpochMillis = null,
            chatGptLogin = null,
            chatGptModels = emptyList(),
            chatGptModelsLoading = false,
            chatGptResponse = null,
            chatGptElapsedMillis = null,
            chatGptStatusMessage = "ChatGPTからログアウトしました。",
            chatGptError = null,
          )
        }
      }
      .onFailure(::showChatGptError)
  }

  fun refreshChatGptModels() {
    val current = _state.value
    if (!current.chatGptConnected || current.chatGptModelsLoading) return
    _state.update { it.copy(chatGptModelsLoading = true, chatGptError = null) }
    viewModelScope.launch {
      try {
        val models = chatGptProviderRepository.listModels()
        val selected = chatGptProviderRepository.selectedModelId()
        _state.update {
          it.copy(
            chatGptModels = models,
            chatGptSelectedModelId = selected,
            chatGptModelId = selected ?: it.chatGptModelId,
            chatGptStatusMessage = if (models.isEmpty()) {
              "記事要約に利用できるWeb検索対応モデルが見つかりませんでした。"
            } else {
              it.chatGptStatusMessage
            },
          )
        }
      } catch (error: CancellationException) {
        throw error
      } catch (error: Throwable) {
        showChatGptError(error)
      } finally {
        _state.update { it.copy(chatGptModelsLoading = false) }
      }
    }
  }

  fun selectChatGptModel(modelId: String) {
    val model = _state.value.chatGptModels.firstOrNull { it.id == modelId }
      ?: return
    if (!model.supportsWebSearch) {
      _state.update { it.copy(chatGptError = "記事要約にはWeb検索対応モデルを選択してください。") }
      return
    }
    runCatching { chatGptProviderRepository.selectModel(modelId) }
      .onSuccess {
        _state.update {
          it.copy(
            chatGptSelectedModelId = modelId,
            chatGptModelId = modelId,
            chatGptResponse = null,
            chatGptElapsedMillis = null,
            chatGptStatusMessage = "${model.name} を選択しました。",
            chatGptError = null,
          )
        }
      }
      .onFailure(::showChatGptError)
  }

  fun setSummaryExecutionProvider(provider: SummaryExecutionProvider) {
    if (provider == SummaryExecutionProvider.CHATGPT) {
      val current = _state.value
      if (!current.chatGptConnected) {
        _state.update { it.copy(chatGptError = "ChatGPTへログインしてください。") }
        return
      }
      if (current.chatGptSelectedModelId == null) {
        _state.update { it.copy(chatGptError = "ChatGPT / Codex の利用モデルを選択してください。") }
        return
      }
    }
    runCatching { summaryExecutionSettings.setProvider(provider) }
      .onFailure(::showChatGptError)
  }

  fun setChatGptModelId(modelId: String) {
    _state.update { it.copy(chatGptModelId = modelId, chatGptResponse = null, chatGptElapsedMillis = null) }
  }

  fun setChatGptPrompt(prompt: String) {
    _state.update { it.copy(chatGptPrompt = prompt, chatGptResponse = null, chatGptElapsedMillis = null) }
  }

  fun runChatGptDebugInference() {
    val current = _state.value
    val modelId = current.chatGptSelectedModelId ?: return
    if (current.chatGptBusy || current.chatGptPrompt.isBlank()) return
    _state.update {
      it.copy(
        chatGptBusy = true,
        chatGptResponse = null,
        chatGptElapsedMillis = null,
        chatGptStatusMessage = null,
        chatGptError = null,
      )
    }
    viewModelScope.launch {
      try {
        val result = chatGptDebugRepository.runInference(modelId, current.chatGptPrompt)
        _state.update {
          it.copy(
            chatGptResponse = result.text,
            chatGptElapsedMillis = result.elapsedMillis,
            chatGptStatusMessage = "${result.modelId} への推論リクエストが成功しました。",
          )
        }
        refreshChatGptStatus(clearTransientState = false)
      } catch (error: CancellationException) {
        throw error
      } catch (error: Throwable) {
        showChatGptError(error)
      } finally {
        _state.update { it.copy(chatGptBusy = false) }
      }
    }
  }

  fun updateSummaryPrompt(prompt: String) {
    runCatching { summaryPromptSettings.update(prompt) }
      .onSuccess { _state.update { it.copy(message = "要約プロンプトを保存しました") } }
      .onFailure(::showError)
  }

  fun resetSummaryPrompt() {
    runCatching { summaryPromptSettings.reset() }
      .onSuccess { _state.update { it.copy(message = "要約プロンプトを既定に戻しました") } }
      .onFailure(::showError)
  }

  fun setInferenceBackend(backend: AiInferenceBackend) {
    clearBenchmark(clearContextResult = true)
    runCatching { repository.setInferenceBackend(backend) }.onFailure(::showError)
  }

  fun setContextSizeMode(mode: AiContextSizeMode) {
    clearBenchmark()
    runCatching { repository.setContextSizeMode(mode) }.onFailure(::showError)
  }

  fun setThinkingEnabled(enabled: Boolean) {
    runCatching { repository.setThinkingEnabled(enabled) }.onFailure(::showError)
  }

  fun setSpeculativeDecodingEnabled(enabled: Boolean) {
    clearBenchmark(clearContextResult = true)
    runCatching { repository.setSpeculativeDecodingEnabled(enabled) }.onFailure(::showError)
  }

  fun runModelBenchmark() {
    if (_state.value.benchmarkRunning) return
    _state.update {
      it.copy(
        benchmarkRunning = true,
        benchmarkResult = null,
        benchmarkError = null,
        contextBenchmarkError = null,
      )
    }
    viewModelScope.launch {
      try {
        val result = repository.benchmarkSelectedModel()
        _state.update { it.copy(benchmarkResult = result) }
      } catch (error: CancellationException) {
        throw error
      } catch (error: Throwable) {
        _state.update { it.copy(benchmarkError = error.userMessage()) }
      } finally {
        _state.update { it.copy(benchmarkRunning = false) }
      }
    }
  }

  fun runContextBenchmark() {
    if (_state.value.benchmarkRunning) return
    _state.update {
      it.copy(
        benchmarkRunning = true,
        benchmarkResult = null,
        benchmarkError = null,
        contextBenchmarkError = null,
      )
    }
    viewModelScope.launch {
      try {
        val result = repository.benchmarkSelectedModelContexts()
        _state.update { it.copy(contextBenchmarkResult = result) }
      } catch (error: CancellationException) {
        throw error
      } catch (error: Throwable) {
        _state.update { it.copy(contextBenchmarkError = error.userMessage()) }
      } finally {
        _state.update { it.copy(benchmarkRunning = false) }
      }
    }
  }

  fun downloadModel(modelId: String) {
    runCatching { repository.downloadModel(modelId) }
      .onSuccess { _state.update { it.copy(message = "モデルのバックグラウンドダウンロードを開始しました") } }
      .onFailure(::showError)
  }

  fun selectModel(modelId: String) {
    clearBenchmark(clearContextResult = true)
    runCatching { repository.selectModel(modelId) }.onFailure(::showError)
  }

  fun deleteModel(modelId: String) {
    clearBenchmark(clearContextResult = true)
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { repository.deleteModel(modelId) }
        .onSuccess { _state.update { it.copy(message = "モデルを削除しました") } }
        .onFailure(::showError)
    }
  }

  fun dismissMessage() {
    _state.update { it.copy(message = null) }
  }

  private fun refreshChatGptStatus(clearTransientState: Boolean) {
    val status = runCatching { chatGptDebugRepository.status() }
      .getOrElse {
        showChatGptError(it)
        return
      }
    val selected = chatGptProviderRepository.selectedModelId()
    _state.update {
      it.copy(
        chatGptConnected = status.connected,
        chatGptAccountLabel = status.accountLabel,
        chatGptExpiresAtEpochMillis = status.expiresAtEpochMillis,
        chatGptSelectedModelId = selected,
        chatGptModelId = selected ?: it.chatGptModelId,
        chatGptLogin = if (clearTransientState) null else it.chatGptLogin,
        chatGptResponse = if (clearTransientState) null else it.chatGptResponse,
        chatGptElapsedMillis = if (clearTransientState) null else it.chatGptElapsedMillis,
        chatGptStatusMessage = if (clearTransientState) null else it.chatGptStatusMessage,
        chatGptError = null,
      )
    }
  }

  private fun refreshContextBenchmarkIfNeeded() {
    val current = _state.value
    val key = ContextBenchmarkKey(
      modelId = current.models.firstOrNull(AiModelStatus::selected)?.id,
      backend = current.inferenceBackend,
      speculativeDecodingEnabled = current.speculativeDecodingEnabled,
    )
    if (lastContextBenchmarkKey == key) return
    lastContextBenchmarkKey = key
    _state.update { it.copy(contextBenchmarkResult = null, contextBenchmarkError = null) }

    viewModelScope.launch(Dispatchers.IO) {
      try {
        val report = repository.lastContextBenchmark()
        if (lastContextBenchmarkKey == key) _state.update { it.copy(contextBenchmarkResult = report) }
      } catch (error: CancellationException) {
        throw error
      } catch (error: Throwable) {
        if (lastContextBenchmarkKey == key) _state.update { it.copy(contextBenchmarkError = error.userMessage()) }
      }
    }
  }

  private fun clearBenchmark(clearContextResult: Boolean = false) {
    _state.update {
      it.copy(
        benchmarkResult = null,
        benchmarkError = null,
        contextBenchmarkResult = if (clearContextResult) null else it.contextBenchmarkResult,
        contextBenchmarkError = null,
      )
    }
  }

  private fun showError(error: Throwable) {
    _state.update { it.copy(message = error.userMessage()) }
  }

  private fun showChatGptError(error: Throwable) {
    _state.update { it.copy(chatGptError = error.userMessage(), chatGptStatusMessage = null) }
  }

  class Factory(
    private val repository: AiModelRepository,
    private val summaryPromptSettings: SummaryPromptSettings,
    private val chatGptDebugRepository: ChatGptDebugRepository,
    private val chatGptProviderRepository: ChatGptProviderRepository,
    private val summaryExecutionSettings: SummaryExecutionSettings,
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      require(modelClass.isAssignableFrom(AiSettingsViewModel::class.java))
      @Suppress("UNCHECKED_CAST")
      return AiSettingsViewModel(
        repository,
        summaryPromptSettings,
        chatGptDebugRepository,
        chatGptProviderRepository,
        summaryExecutionSettings,
      ) as T
    }
  }
}

private fun Throwable.userMessage(): String =
  generateSequence(this) { cause -> cause.cause }
    .mapNotNull(Throwable::message)
    .firstOrNull(String::isNotBlank)
    ?: javaClass.simpleName
