package dev.terashima.yomitorirss.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
  val initialized: Boolean = false,
  val sessions: List<ChatSession> = emptyList(),
  val activeSessionId: String? = null,
  val messages: List<StoredChatMessage> = emptyList(),
  val selectedModel: ChatModelStatus? = null,
  val progress: ChatProgress? = null,
  val streamingReply: String = "",
  val sending: Boolean = false,
  val responseStarted: Boolean = false,
  val errorText: String? = null,
)

class ChatViewModel(
  private val repository: ChatRepository,
  private val generator: ChatGenerator,
) : ViewModel() {
  private val _state = MutableStateFlow(ChatUiState())
  val state: StateFlow<ChatUiState> = _state.asStateFlow()

  init {
    viewModelScope.launch {
      generator.selectedModel.collect { selectedModel ->
        _state.update { it.copy(selectedModel = selectedModel) }
      }
    }
    viewModelScope.launch {
      generator.progress.collect { progress ->
        _state.update { it.copy(progress = progress) }
      }
    }
    viewModelScope.launch {
      generator.streamingReply.collect { reply ->
        _state.update { state ->
          if (!state.sending) {
            state.copy(streamingReply = "")
          } else {
            state.copy(
              streamingReply = reply,
              responseStarted = state.responseStarted || reply.isNotBlank(),
            )
          }
        }
      }
    }
    viewModelScope.launch(Dispatchers.IO) { loadInitialState() }
  }

  fun selectSession(sessionId: String) {
    if (_state.value.sending || sessionId == _state.value.activeSessionId) return
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { repository.listMessages(sessionId) }
        .onSuccess { messages ->
          _state.update {
            it.copy(
              activeSessionId = sessionId,
              messages = messages,
              errorText = null,
              streamingReply = "",
              responseStarted = false,
            )
          }
        }
        .onFailure(::showError)
    }
  }

  fun startNewSession() {
    if (_state.value.sending) return
    _state.update {
      it.copy(
        activeSessionId = null,
        messages = emptyList(),
        streamingReply = "",
        responseStarted = false,
        errorText = null,
      )
    }
  }

  fun sendMessage(text: String) {
    val normalized = text.trim()
    val current = _state.value
    if (normalized.isBlank() || current.sending || current.selectedModel == null) return

    _state.update {
      it.copy(
        sending = true,
        responseStarted = false,
        streamingReply = "",
        errorText = null,
      )
    }

    viewModelScope.launch(Dispatchers.IO) {
      try {
        val sessionId = _state.value.activeSessionId
          ?: repository.createSession(normalized).id.also { createdId ->
            _state.update { it.copy(activeSessionId = createdId) }
          }

        repository.appendMessage(sessionId, ChatRole.USER, normalized)
        val turns = reloadSession(sessionId).map { ChatTurn(it.role, it.content) }
        val reply = generator.reply(turns)
        repository.appendMessage(sessionId, ChatRole.ASSISTANT, reply)
        reloadSession(sessionId)
      } catch (error: Throwable) {
        showError(error)
        _state.value.activeSessionId?.let { sessionId ->
          runCatching { reloadSession(sessionId) }
        }
      } finally {
        _state.update {
          it.copy(
            sending = false,
            responseStarted = false,
            streamingReply = "",
          )
        }
      }
    }
  }

  private suspend fun loadInitialState() {
    runCatching {
      val sessions = repository.listSessions()
      val activeSessionId = sessions.firstOrNull()?.id
      val messages = activeSessionId?.let { repository.listMessages(it) }.orEmpty()
      Triple(sessions, activeSessionId, messages)
    }.onSuccess { (sessions, activeSessionId, messages) ->
      _state.update {
        it.copy(
          initialized = true,
          sessions = sessions,
          activeSessionId = activeSessionId,
          messages = messages,
        )
      }
    }.onFailure { error ->
      _state.update { it.copy(initialized = true, errorText = error.userMessage()) }
    }
  }

  private suspend fun reloadSession(sessionId: String): List<StoredChatMessage> {
    val messages = repository.listMessages(sessionId)
    val sessions = repository.listSessions()
    _state.update {
      it.copy(
        sessions = sessions,
        activeSessionId = sessionId,
        messages = messages,
      )
    }
    return messages
  }

  private fun showError(error: Throwable) {
    _state.update { it.copy(errorText = error.userMessage()) }
  }

  class Factory(
    private val repository: ChatRepository,
    private val generator: ChatGenerator,
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      require(modelClass.isAssignableFrom(ChatViewModel::class.java)) {
        "Unknown ViewModel class: ${modelClass.name}"
      }
      @Suppress("UNCHECKED_CAST")
      return ChatViewModel(repository, generator) as T
    }
  }
}

private fun Throwable.userMessage(): String =
  message?.takeIf(String::isNotBlank) ?: "AIチャットの処理に失敗しました"
