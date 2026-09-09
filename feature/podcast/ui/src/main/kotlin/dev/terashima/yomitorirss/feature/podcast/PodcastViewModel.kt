package dev.terashima.yomitorirss.feature.podcast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.terashima.yomitorirss.feature.audio.AudioPlaybackController
import dev.terashima.yomitorirss.feature.audio.AudioQueueItem
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PodcastUiState(
  val initialized: Boolean = false,
  val programs: List<PodcastProgram> = emptyList(),
  val sources: List<PodcastSource> = emptyList(),
  val selectedProgramId: String? = null,
  val episodes: List<PodcastEpisode> = emptyList(),
  val busyProgramIds: Set<String> = emptySet(),
  val busyEpisodeIds: Set<String> = emptySet(),
  val message: String? = null,
) {
  val selectedProgram: PodcastProgram?
    get() = programs.firstOrNull { it.id == selectedProgramId }
}

class PodcastViewModel(
  private val repository: PodcastRepository,
  private val generatePodcastEpisode: GeneratePodcastEpisodeUseCase,
  private val scheduleController: PodcastScheduleController,
  private val audioPlaybackController: AudioPlaybackController,
) : ViewModel() {
  private val _state = MutableStateFlow(PodcastUiState())
  val state: StateFlow<PodcastUiState> = _state.asStateFlow()

  init {
    reload()
  }

  fun reload() {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching {
        val programs = repository.listPrograms()
        val sources = repository.listSources()
        val selectedId = _state.value.selectedProgramId
          ?.takeIf { id -> programs.any { it.id == id } }
          ?: programs.firstOrNull()?.id
        val episodes = selectedId?.let { repository.listEpisodes(it) }.orEmpty()
        Triple(programs, sources, selectedId to episodes)
      }.onSuccess { (programs, sources, selection) ->
        _state.update {
          it.copy(
            initialized = true,
            programs = programs,
            sources = sources,
            selectedProgramId = selection.first,
            episodes = selection.second,
            message = null,
          )
        }
      }.onFailure(::showError)
    }
  }

  fun selectProgram(programId: String) {
    _state.update { it.copy(selectedProgramId = programId, episodes = emptyList(), message = null) }
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { repository.listEpisodes(programId) }
        .onSuccess { episodes -> _state.update { it.copy(episodes = episodes) } }
        .onFailure(::showError)
    }
  }

  fun saveSource(name: String, feedUrl: String) {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching {
        repository.saveSource(
          PodcastSource(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            feedUrl = feedUrl.trim(),
          ),
        )
      }.onSuccess {
        _state.update { it.copy(message = "ソースを追加しました") }
        reload()
      }.onFailure(::showError)
    }
  }

  fun saveProgram(
    id: String?,
    name: String,
    sourceIds: Set<String>,
    provider: PodcastGenerationProvider,
    scheduleEnabled: Boolean,
    scheduleHour: Int,
    scheduleMinute: Int,
    maxArticles: Int,
  ) {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching {
        val program = PodcastProgram(
          id = id ?: UUID.randomUUID().toString(),
          name = name.trim(),
          sourceIds = sourceIds,
          provider = provider,
          schedule = PodcastSchedule(scheduleEnabled, scheduleHour, scheduleMinute),
          maxArticlesPerEpisode = maxArticles,
        )
        repository.saveProgram(program)
        scheduleController.sync(program)
        program
      }.onSuccess { program ->
        _state.update { it.copy(selectedProgramId = program.id, message = "番組を保存しました") }
        reload()
      }.onFailure(::showError)
    }
  }

  fun deleteProgram(programId: String) {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching {
        scheduleController.cancel(programId)
        repository.deleteProgram(programId)
      }.onSuccess {
        _state.update { it.copy(selectedProgramId = null, message = "番組を削除しました") }
        reload()
      }.onFailure(::showError)
    }
  }

  fun generate(programId: String) {
    _state.update { it.copy(busyProgramIds = it.busyProgramIds + programId, message = null) }
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { generatePodcastEpisode.generate(programId) }
        .onSuccess { result ->
          _state.update {
            it.copy(
              busyProgramIds = it.busyProgramIds - programId,
              message = when (result) {
                is PodcastGenerationResult.Generated -> "エピソードを生成しました"
                PodcastGenerationResult.NoNewArticles -> "新しい記事はありません"
              },
            )
          }
          refreshEpisodes(programId)
        }
        .onFailure { error ->
          _state.update { it.copy(busyProgramIds = it.busyProgramIds - programId) }
          showError(error)
          refreshEpisodes(programId)
        }
    }
  }

  fun retry(episodeId: String) {
    _state.update { it.copy(busyEpisodeIds = it.busyEpisodeIds + episodeId, message = null) }
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { generatePodcastEpisode.retry(episodeId) }
        .onSuccess { generated ->
          _state.update {
            it.copy(
              busyEpisodeIds = it.busyEpisodeIds - episodeId,
              message = "エピソードを再生成しました",
            )
          }
          refreshEpisodes(generated.episode.programId)
        }
        .onFailure { error ->
          _state.update { it.copy(busyEpisodeIds = it.busyEpisodeIds - episodeId) }
          showError(error)
        }
    }
  }

  fun play(episode: PodcastEpisode) {
    val script = episode.script?.takeIf(String::isNotBlank) ?: return
    val programName = _state.value.programs.firstOrNull { it.id == episode.programId }?.name
    audioPlaybackController.play(
      listOf(
        AudioQueueItem(
          contentId = "podcast:${episode.id}",
          title = episode.title,
          source = programName,
          speechText = script,
        ),
      ),
    )
  }

  fun clearMessage() {
    _state.update { it.copy(message = null) }
  }

  private suspend fun refreshEpisodes(programId: String) {
    val episodes = repository.listEpisodes(programId)
    _state.update { current ->
      if (current.selectedProgramId == programId) current.copy(episodes = episodes) else current
    }
  }

  private fun showError(error: Throwable) {
    _state.update {
      it.copy(
        initialized = true,
        message = error.message ?: "ニュースポッドキャストの処理に失敗しました",
      )
    }
  }

  class Factory(
    private val repository: PodcastRepository,
    private val generatePodcastEpisode: GeneratePodcastEpisodeUseCase,
    private val scheduleController: PodcastScheduleController,
    private val audioPlaybackController: AudioPlaybackController,
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      require(modelClass.isAssignableFrom(PodcastViewModel::class.java))
      @Suppress("UNCHECKED_CAST")
      return PodcastViewModel(
        repository,
        generatePodcastEpisode,
        scheduleController,
        audioPlaybackController,
      ) as T
    }
  }
}
