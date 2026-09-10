package dev.terashima.yomitorirss.composition.podcast

import android.app.Application
import dev.terashima.yomitorirss.core.aiinference.AiTextInference
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.database.PersistenceChangeNotifier
import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.feature.audio.AudioPlaybackController
import dev.terashima.yomitorirss.feature.podcast.GeneratePodcastEpisodeUseCase
import dev.terashima.yomitorirss.feature.podcast.PodcastGenerationTaskReader
import dev.terashima.yomitorirss.feature.podcast.PodcastProgram
import dev.terashima.yomitorirss.feature.podcast.PodcastScheduleController
import dev.terashima.yomitorirss.feature.podcast.PodcastViewModel
import dev.terashima.yomitorirss.feature.podcast.data.DefaultPodcastScriptGenerator
import dev.terashima.yomitorirss.feature.podcast.data.PodcastGenerationWorkerFactory
import dev.terashima.yomitorirss.feature.podcast.data.RssPodcastFeedContentSource
import dev.terashima.yomitorirss.feature.podcast.data.SqlitePodcastRepository
import dev.terashima.yomitorirss.feature.podcast.data.WorkManagerPodcastScheduleController
import dev.terashima.yomitorirss.feature.rss.data.DefaultRssFeedContentReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal class AppPodcastRuntimeDependencies(
  application: Application,
  database: DatabaseConnection,
  httpClient: HttpClient,
  localTextInference: AiTextInference,
  cloudTextInference: AiTextInference,
  audioPlaybackController: AudioPlaybackController,
  private val persistenceChanges: PersistenceChangeNotifier = PersistenceChangeNotifier.shared,
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val repository = SqlitePodcastRepository(database)
  val taskReader: PodcastGenerationTaskReader = repository
  private val generationUseCase = GeneratePodcastEpisodeUseCase(
    repository = repository,
    feedContentSource = RssPodcastFeedContentSource(
      reader = DefaultRssFeedContentReader(database, httpClient),
    ),
    scriptGenerator = DefaultPodcastScriptGenerator(localTextInference, cloudTextInference),
  )
  private val scheduleController = WorkManagerPodcastScheduleController(application)

  val viewModelFactory = PodcastViewModel.Factory(
    repository = repository,
    generatePodcastEpisode = generationUseCase,
    scheduleController = scheduleController,
    audioPlaybackController = audioPlaybackController,
  )

  val workerFactory = PodcastGenerationWorkerFactory(repository, generationUseCase, scheduleController)

  fun restoreSchedules() {
    scope.launch { resumeInterruptedGenerations() }
    scope.launch {
      var previousPrograms = emptyMap<String, PodcastProgram>()
      persistenceChanges.version.collect {
        runCatching { repository.listPrograms().associateBy(PodcastProgram::id) }
          .onSuccess { currentPrograms ->
            reconcilePodcastSchedules(previousPrograms, currentPrograms, scheduleController)
            previousPrograms = currentPrograms
          }
      }
    }
  }

  private suspend fun resumeInterruptedGenerations() {
    val programs = try {
      repository.listPrograms()
    } catch (error: CancellationException) {
      throw error
    } catch (_: Throwable) {
      return
    }
    programs.forEach { program ->
      try {
        generationUseCase.resumeInterrupted(program.id)
      } catch (error: CancellationException) {
        throw error
      } catch (_: Throwable) {
        // Normal generation errors are persisted by the generation use case. A concurrent active
        // generation also wins through the program-level guard, so startup recovery can stop.
      }
    }
  }
}

internal fun reconcilePodcastSchedules(
  previousPrograms: Map<String, PodcastProgram>,
  currentPrograms: Map<String, PodcastProgram>,
  scheduleController: PodcastScheduleController,
) {
  (previousPrograms.keys - currentPrograms.keys).forEach(scheduleController::cancel)
  currentPrograms.forEach { (programId, program) ->
    when {
      programId !in previousPrograms -> scheduleController.ensure(program)
      previousPrograms[programId] != program -> scheduleController.sync(program)
    }
  }
}
