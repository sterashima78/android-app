package dev.terashima.yomitorirss.feature.podcast.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import dev.terashima.yomitorirss.core.background.CloudAiBackgroundExecutionPreferences
import dev.terashima.yomitorirss.core.background.LocalAiBackgroundExecutionPreferences
import dev.terashima.yomitorirss.feature.podcast.GeneratePodcastEpisodeUseCase
import dev.terashima.yomitorirss.feature.podcast.PodcastGenerationAlreadyRunningException
import dev.terashima.yomitorirss.feature.podcast.PodcastGenerationController
import dev.terashima.yomitorirss.feature.podcast.PodcastGenerationProgress
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
    val operation = podcastGenerationOperation(inputData.getString(KEY_OPERATION))
    var scheduleNext = false

    return try {
      setForeground(createForegroundInfo(program.name, null))
      if (
        operation == PodcastGenerationOperation.SCHEDULED_GENERATE &&
        shouldSkipPodcastGeneration(
          provider = program.provider,
          localPaused = LocalAiBackgroundExecutionPreferences(applicationContext).paused,
          cloudPaused = CloudAiBackgroundExecutionPreferences(applicationContext).paused,
        )
      ) {
        scheduleNext = true
        return Result.success()
      }

      val onProgress: suspend (PodcastGenerationProgress) -> Unit = { progress ->
        setProgress(
          Data.Builder()
            .putInt(KEY_COMPLETED_CHAPTERS, progress.completedChapters)
            .putInt(KEY_TOTAL_CHAPTERS, progress.totalChapters)
            .build(),
        )
        setForeground(createForegroundInfo(program.name, progress))
      }

      try {
        when (operation) {
          PodcastGenerationOperation.SCHEDULED_GENERATE,
          PodcastGenerationOperation.GENERATE -> generatePodcastEpisode.generate(programId, onProgress)
          PodcastGenerationOperation.REGENERATE -> {
            val episodeId = inputData.getString(KEY_EPISODE_ID) ?: return Result.failure()
            generatePodcastEpisode.regenerate(episodeId, onProgress)
          }
        }
        scheduleNext = operation == PodcastGenerationOperation.SCHEDULED_GENERATE
        Result.success()
      } catch (error: PodcastGenerationAlreadyRunningException) {
        Result.retry()
      } catch (error: CancellationException) {
        throw error
      } catch (_: Throwable) {
        // Generation persists episode/chapter failures itself. Scheduled runs still converge to the
        // next local occurrence; manual failures remain observable through Podcast durable state.
        scheduleNext = operation == PodcastGenerationOperation.SCHEDULED_GENERATE
        Result.success()
      }
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

  private fun createForegroundInfo(
    programName: String,
    progress: PodcastGenerationProgress?,
  ): ForegroundInfo {
    val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
    notificationManager.createNotificationChannel(
      NotificationChannel(CHANNEL_ID, "ニュースポッドキャスト生成", NotificationManager.IMPORTANCE_LOW).apply {
        description = "ニュースポッドキャストをバックグラウンドで生成している間に表示します"
        setShowBadge(false)
      },
    )
    val contentText = podcastGenerationProgressText(programName, progress)
    val notificationBuilder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.stat_notify_sync)
      .setContentTitle("ニュースポッドキャストを生成中")
      .setContentText(contentText)
      .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setCategory(NotificationCompat.CATEGORY_PROGRESS)
      .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

    if (progress != null && progress.totalChapters > 0) {
      notificationBuilder.setProgress(progress.totalChapters, progress.completedChapters, false)
    } else {
      notificationBuilder.setProgress(0, 0, true)
    }

    applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)?.let { launchIntent ->
      PendingIntent.getActivity(
        applicationContext,
        notificationId(),
        launchIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    }?.let(notificationBuilder::setContentIntent)

    return ForegroundInfo(
      notificationId(),
      notificationBuilder.build(),
      ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
    )
  }

  private fun notificationId(): Int = NOTIFICATION_ID_BASE + (id.hashCode() and NOTIFICATION_ID_MASK)

  companion object {
    const val KEY_PROGRAM_ID = "program_id"
    const val KEY_OPERATION = "operation"
    const val KEY_EPISODE_ID = "episode_id"
    const val KEY_COMPLETED_CHAPTERS = "completed_chapters"
    const val KEY_TOTAL_CHAPTERS = "total_chapters"

    private const val CHANNEL_ID = "podcast_generation"
    private const val NOTIFICATION_ID_BASE = 12_000
    private const val NOTIFICATION_ID_MASK = 0x3fff
  }
}

enum class PodcastGenerationOperation {
  SCHEDULED_GENERATE,
  GENERATE,
  REGENERATE,
}

internal fun podcastGenerationOperation(raw: String?): PodcastGenerationOperation =
  raw?.let { value -> runCatching { PodcastGenerationOperation.valueOf(value) }.getOrNull() }
    ?: PodcastGenerationOperation.SCHEDULED_GENERATE

internal fun podcastGenerationProgressText(
  programName: String,
  progress: PodcastGenerationProgress?,
): String = when {
  progress == null || progress.totalChapters == 0 -> "$programName・準備中"
  else -> "$programName・${progress.completedChapters}/${progress.totalChapters} チャプター"
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

class WorkManagerPodcastGenerationController(
  context: Context,
) : PodcastGenerationController {
  private val workManager = WorkManager.getInstance(context.applicationContext)

  override fun generate(programId: String) {
    enqueue(programId, PodcastGenerationOperation.GENERATE, episodeId = null)
  }

  override fun regenerate(programId: String, episodeId: String) {
    enqueue(programId, PodcastGenerationOperation.REGENERATE, episodeId)
  }

  private fun enqueue(
    programId: String,
    operation: PodcastGenerationOperation,
    episodeId: String?,
  ) {
    val data = Data.Builder()
      .putString(PodcastGenerationWorker.KEY_PROGRAM_ID, programId)
      .putString(PodcastGenerationWorker.KEY_OPERATION, operation.name)
      .apply { episodeId?.let { putString(PodcastGenerationWorker.KEY_EPISODE_ID, it) } }
      .build()
    val request = OneTimeWorkRequestBuilder<PodcastGenerationWorker>()
      .setInputData(data)
      .setConstraints(
        Constraints.Builder()
          .setRequiredNetworkType(NetworkType.CONNECTED)
          .build(),
      )
      .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequestMinimumBackoff.SECONDS, TimeUnit.SECONDS)
      .build()

    workManager.enqueueUniqueWork(manualWorkName(programId), ExistingWorkPolicy.KEEP, request)
  }

  private fun manualWorkName(programId: String): String = "podcast-manual-$programId"
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
    workManager.cancelUniqueWork("podcast-manual-$programId")
  }

  private fun enqueue(program: PodcastProgram, policy: ExistingWorkPolicy) {
    if (!program.schedule.enabled) {
      workManager.cancelUniqueWork(workName(program.id))
      return
    }

    val request = OneTimeWorkRequestBuilder<PodcastGenerationWorker>()
      .setInputData(
        Data.Builder()
          .putString(PodcastGenerationWorker.KEY_PROGRAM_ID, program.id)
          .putString(PodcastGenerationWorker.KEY_OPERATION, PodcastGenerationOperation.SCHEDULED_GENERATE.name)
          .build(),
      )
      .setInitialDelay(nextRunDelayMillis(now(), program.schedule.hour, program.schedule.minute), TimeUnit.MILLISECONDS)
      .setConstraints(
        Constraints.Builder()
          .setRequiredNetworkType(NetworkType.CONNECTED)
          .build(),
      )
      .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequestMinimumBackoff.SECONDS, TimeUnit.SECONDS)
      .build()

    workManager.enqueueUniqueWork(workName(program.id), policy, request)
  }

  private fun workName(programId: String): String = "podcast-program-$programId"
}

private object WorkRequestMinimumBackoff {
  const val SECONDS = 10L
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
