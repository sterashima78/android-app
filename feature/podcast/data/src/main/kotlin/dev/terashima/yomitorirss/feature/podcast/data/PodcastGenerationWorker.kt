package dev.terashima.yomitorirss.feature.podcast.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import dev.terashima.yomitorirss.core.background.CloudAiBackgroundExecutionPreferences
import dev.terashima.yomitorirss.core.background.LocalAiBackgroundExecutionPreferences
import dev.terashima.yomitorirss.feature.podcast.GeneratePodcastEpisodeUseCase
import dev.terashima.yomitorirss.feature.podcast.PodcastGenerationProvider
import dev.terashima.yomitorirss.feature.podcast.PodcastProgram
import dev.terashima.yomitorirss.feature.podcast.PodcastRepository
import dev.terashima.yomitorirss.feature.podcast.PodcastScheduleController
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

class PodcastGenerationWorker(
  appContext: Context,
  params: WorkerParameters,
  private val repository: PodcastRepository,
  private val generatePodcastEpisode: GeneratePodcastEpisodeUseCase,
  private val scheduleController: PodcastScheduleController,
) : CoroutineWorker(appContext, params) {
  override suspend fun doWork(): Result {
    val programId = inputData.getString(KEY_PROGRAM_ID) ?: return Result.failure()
    val program = repository.findProgram(programId) ?: return Result.success()
    var scheduleNext = true
    return try {
      val localPaused = LocalAiBackgroundExecutionPreferences(applicationContext).paused
      val cloudPaused = CloudAiBackgroundExecutionPreferences(applicationContext).paused
      if (!shouldSkipPodcastGeneration(program.provider, localPaused, cloudPaused)) {
        try {
          generatePodcastEpisode.generate(programId)
        } catch (error: CancellationException) {
          throw error
        } catch (_: Throwable) {
          // Generation persists its episode failure. The scheduled chain itself stays successful so
          // the next daily occurrence remains eligible to run.
        }
      }
      Result.success()
    } catch (error: CancellationException) {
      scheduleNext = false
      throw error
    } finally {
      if (scheduleNext) {
        val currentProgram = try {
          repository.findProgram(programId)
        } catch (_: Throwable) {
          null
        }
        currentProgram?.let(scheduleController::scheduleNext)
      }
    }
  }

  companion object {
    const val KEY_PROGRAM_ID = "program_id"
  }
}

class PodcastGenerationWorkerFactory(
  private val repository: PodcastRepository,
  private val generatePodcastEpisode: GeneratePodcastEpisodeUseCase,
  private val scheduleController: PodcastScheduleController,
) : WorkerFactory() {
  override fun createWorker(
    appContext: Context,
    workerClassName: String,
    workerParameters: WorkerParameters,
  ): ListenableWorker? = if (workerClassName == PodcastGenerationWorker::class.java.name) {
    PodcastGenerationWorker(
      appContext,
      workerParameters,
      repository,
      generatePodcastEpisode,
      scheduleController,
    )
  } else {
    null
  }
}

class WorkManagerPodcastScheduleController(
  context: Context,
  private val now: () -> ZonedDateTime = ZonedDateTime::now,
) : PodcastScheduleController {
  private val appContext = context.applicationContext
  private val workManager: WorkManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    WorkManager.getInstance(appContext)
  }

  override fun sync(program: PodcastProgram) {
    enqueue(program, ExistingWorkPolicy.REPLACE)
  }

  override fun ensure(program: PodcastProgram) {
    enqueue(program, ExistingWorkPolicy.KEEP)
  }

  override fun scheduleNext(program: PodcastProgram) {
    enqueue(program, ExistingWorkPolicy.APPEND_OR_REPLACE)
  }

  override fun cancel(programId: String) {
    workManager.cancelUniqueWork(workName(programId))
  }

  private fun enqueue(program: PodcastProgram, policy: ExistingWorkPolicy) {
    if (!program.schedule.enabled) {
      cancel(program.id)
      return
    }

    val request = OneTimeWorkRequestBuilder<PodcastGenerationWorker>()
      .setInputData(Data.Builder().putString(PodcastGenerationWorker.KEY_PROGRAM_ID, program.id).build())
      .setInitialDelay(nextRunDelayMillis(now(), program.schedule.hour, program.schedule.minute), TimeUnit.MILLISECONDS)
      .setConstraints(
        Constraints.Builder()
          .setRequiredNetworkType(NetworkType.CONNECTED)
          .build(),
      )
      .build()

    workManager.enqueueUniqueWork(workName(program.id), policy, request)
  }

  private fun workName(programId: String): String = "podcast-program-$programId"
}

internal fun shouldSkipPodcastGeneration(
  provider: PodcastGenerationProvider,
  localPaused: Boolean,
  cloudPaused: Boolean,
): Boolean = when (provider) {
  PodcastGenerationProvider.LOCAL -> localPaused
  PodcastGenerationProvider.CLOUD -> cloudPaused
}

internal fun nextRunDelayMillis(now: ZonedDateTime, hour: Int, minute: Int): Long {
  var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
  if (!next.isAfter(now)) next = next.plusDays(1)
  return Duration.between(now, next).toMillis().coerceAtLeast(0L)
}
