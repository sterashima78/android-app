package dev.terashima.yomitorirss.feature.podcast.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
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
) : CoroutineWorker(appContext, params) {
  override suspend fun doWork(): Result {
    val programId = inputData.getString(KEY_PROGRAM_ID) ?: return Result.failure()
    val program = repository.findProgram(programId) ?: return Result.failure()
    val localPaused = LocalAiBackgroundExecutionPreferences(applicationContext).paused
    val cloudPaused = CloudAiBackgroundExecutionPreferences(applicationContext).paused
    if (shouldSkipPodcastGeneration(program.provider, localPaused, cloudPaused)) {
      return Result.success()
    }

    return try {
      generatePodcastEpisode.generate(programId)
      Result.success()
    } catch (error: CancellationException) {
      throw error
    } catch (_: Throwable) {
      // The use case persists the reserved episode as FAILED. Retrying this periodic work by
      // program id would reserve a different set of articles; retry is intentionally explicit by
      // episode id so the stored article snapshot is reused.
      Result.failure()
    }
  }

  companion object {
    const val KEY_PROGRAM_ID = "program_id"
  }
}

class PodcastGenerationWorkerFactory(
  private val repository: PodcastRepository,
  private val generatePodcastEpisode: GeneratePodcastEpisodeUseCase,
) : WorkerFactory() {
  override fun createWorker(
    appContext: Context,
    workerClassName: String,
    workerParameters: WorkerParameters,
  ): ListenableWorker? = if (workerClassName == PodcastGenerationWorker::class.java.name) {
    PodcastGenerationWorker(appContext, workerParameters, repository, generatePodcastEpisode)
  } else {
    null
  }
}

class WorkManagerPodcastScheduleController(
  context: Context,
  private val now: () -> ZonedDateTime = ZonedDateTime::now,
) : PodcastScheduleController {
  private val appContext = context.applicationContext
  private val workManager = WorkManager.getInstance(appContext)

  override fun sync(program: PodcastProgram) {
    enqueue(program, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE)
  }

  override fun ensure(program: PodcastProgram) {
    enqueue(program, ExistingPeriodicWorkPolicy.KEEP)
  }

  override fun cancel(programId: String) {
    workManager.cancelUniqueWork(workName(programId))
  }

  private fun enqueue(program: PodcastProgram, policy: ExistingPeriodicWorkPolicy) {
    if (!program.schedule.enabled) {
      cancel(program.id)
      return
    }

    val request = PeriodicWorkRequestBuilder<PodcastGenerationWorker>(24, TimeUnit.HOURS)
      .setInputData(Data.Builder().putString(PodcastGenerationWorker.KEY_PROGRAM_ID, program.id).build())
      .setInitialDelay(nextRunDelayMillis(now(), program.schedule.hour, program.schedule.minute), TimeUnit.MILLISECONDS)
      .setConstraints(
        Constraints.Builder()
          .setRequiredNetworkType(NetworkType.CONNECTED)
          .build(),
      )
      .build()

    workManager.enqueueUniquePeriodicWork(workName(program.id), policy, request)
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
