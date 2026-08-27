package dev.terashima.yomitorirss.feature.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorkoutAiUiState(
  val initialized: Boolean = false,
  val date: String = LocalDate.now().toString(),
  val memo: String = "",
  val settings: WorkoutAiSettings = WorkoutAiSettings(),
  val loading: Boolean = false,
  val lastRequestType: WorkoutAiRequestType? = null,
  val response: String? = null,
  val errorMessage: String? = null,
)

class WorkoutAiViewModel(
  private val workoutReader: WorkoutReader,
  private val settingsRepository: WorkoutAiSettingsRepository,
  private val advisor: WorkoutAiAdvisor,
) : ViewModel() {
  private val _state = MutableStateFlow(WorkoutAiUiState())
  val state: StateFlow<WorkoutAiUiState> = _state.asStateFlow()

  init {
    viewModelScope.launch {
      val date = LocalDate.now().toString()
      val settings = settingsRepository.loadSettings()
      val memo = settingsRepository.loadMemo(date)
      _state.value = WorkoutAiUiState(
        initialized = true,
        date = date,
        memo = memo,
        settings = settings,
      )
    }
  }

  fun updateMemo(value: String) {
    val memo = value.take(MAX_MEMO_CHARS)
    _state.update { it.copy(memo = memo) }
    viewModelScope.launch { settingsRepository.saveMemo(_state.value.date, memo) }
  }

  fun setProvider(provider: WorkoutAiProvider) = updateSettings { it.copy(provider = provider) }

  fun updateWorkoutPolicy(value: String) = updateSettings {
    it.copy(workoutPolicy = value.take(MAX_POLICY_CHARS))
  }

  fun requestMenuSuggestion() = request(WorkoutAiRequestType.MENU_SUGGESTION)

  fun requestPostWorkoutReview() = request(WorkoutAiRequestType.POST_WORKOUT_REVIEW)

  fun clearResponse() = _state.update { it.copy(lastRequestType = null, response = null, errorMessage = null) }

  private fun updateSettings(transform: (WorkoutAiSettings) -> WorkoutAiSettings) {
    val settings = transform(_state.value.settings)
    _state.update { it.copy(settings = settings) }
    viewModelScope.launch { settingsRepository.saveSettings(settings) }
  }

  private fun request(type: WorkoutAiRequestType) {
    if (_state.value.loading) return
    _state.update {
      it.copy(
        loading = true,
        lastRequestType = type,
        response = null,
        errorMessage = null,
      )
    }
    viewModelScope.launch {
      try {
        val snapshot = workoutReader.load()
        val settings = _state.value.settings
        val dates = WorkoutAiPromptBuilder.recentDates(snapshot)
        val memos = settingsRepository.loadMemos(dates) + (_state.value.date to _state.value.memo)
        val prompt = WorkoutAiPromptBuilder.build(
          type = type,
          snapshot = snapshot,
          settings = settings,
          memos = memos,
        )
        val response = advisor.generate(settings.provider, prompt).trim()
        _state.update {
          it.copy(
            loading = false,
            response = response.ifBlank { "応答が空でした" },
          )
        }
      } catch (error: CancellationException) {
        throw error
      } catch (error: Throwable) {
        _state.update {
          it.copy(
            loading = false,
            errorMessage = safeErrorMessage(error),
          )
        }
      }
    }
  }

  private fun safeErrorMessage(error: Throwable): String {
    val message = error.message.orEmpty()
    return when {
      message.contains("利用モデルを選択", ignoreCase = true) ->
        "ChatGPT / Codex の利用モデルを選択してください"
      message.contains("認証が無効", ignoreCase = true) ->
        "ChatGPT の認証が無効です。設定から再ログインしてください"
      message.contains("ChatGPT へ接続", ignoreCase = true) ->
        "ChatGPT へ接続してください"
      message.contains("レート制限", ignoreCase = true) ->
        "ChatGPT / Codex の利用上限またはレート制限に達しました"
      message.contains("一時的に利用できません", ignoreCase = true) ->
        "ChatGPT / Codex が一時的に利用できません"
      else -> "AIの応答生成に失敗しました"
    }
  }

  class Factory(
    private val workoutReader: WorkoutReader,
    private val settingsRepository: WorkoutAiSettingsRepository,
    private val advisor: WorkoutAiAdvisor,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      WorkoutAiViewModel(workoutReader, settingsRepository, advisor) as T
  }

  private companion object {
    const val MAX_MEMO_CHARS = 2_000
    const val MAX_POLICY_CHARS = 4_000
  }
}
