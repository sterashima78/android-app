package dev.terashima.yomitorirss.feature.workout.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.await
import androidx.work.workDataOf
import dev.terashima.yomitorirss.core.aiinference.withAiBackgroundInference
import dev.terashima.yomitorirss.core.background.CloudAiBackgroundExecutionPreferences
import dev.terashima.yomitorirss.core.background.LocalAiBackgroundExecutionPreferences
import dev.terashima.yomitorirss.core.background.LocalAiBackgroundTaskGate
import dev.terashima.yomitorirss.feature.workout.WorkoutAiAdvisor
import dev.terashima.yomitorirss.feature.workout.WorkoutAiPromptBuilder
import dev.terashima.yomitorirss.feature.workout.WorkoutAiProvider
import dev.terashima.yomitorirss.feature.workout.WorkoutAiRequestType
import dev.terashima.yomitorirss.feature.workout.WorkoutAiSettingsRepository
import dev.terashima.yomitorirss.feature.workout.WorkoutAiTaskController
import dev.terashima.yomitorirss.feature.workout.WorkoutAiTaskSnapshot
import dev.terashima.yomitorirss.feature.workout.WorkoutAiTaskState
import dev.terashima.yomitorirss.feature.workout.WorkoutReader
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WorkManagerWorkoutAiTaskController(
  context: Context,
  private val settingsRepository: WorkoutAiSettingsRepository,
) : WorkoutAiTaskController {
  private val appContext = context.applicationContext
  private val workManager = WorkManager.getInstance(appContext)

  override suspend fun enqueue(type: WorkoutAiRequestType): String {
    val provider = settingsRepository.loadSettings().provider
    val builder = OneTimeWorkRequestBuilder<WorkoutAiWorker>()
      .setInputData(
        workDataOf(
          KEY_REQUEST_TYPE to type.name,
          KEY_PROVIDER to provider.name,
        ),
      )
      .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
    if (provider == WorkoutAiProvider.CHATGPT) {
      builder.setConstraints(
        Constraints.Builder()
          .setRequiredNetworkType(NetworkType.CONNECTED)
          .build(),
      )
    }
    val request = builder.build()
    workManager.enqueueUniqueWork(
      "$WORK_NAME_PREFIX${type.name.lowercase()}",
      ExistingWorkPolicy.REPLACE,
      request,
    ).await()
    return request.id.toString()
  }

  override suspend fun snapshot(requestId: String): WorkoutAiTaskSnapshot {
    val id = runCatching { UUID.fromString(requestId) }.getOrNull()
      ?: return WorkoutAiTaskSnapshot(
        state = WorkoutAiTaskState.FAILED,
        error = "AIタスクIDが不正です",
      )
    val info = withContext(Dispatchers.IO) { workManager.getWorkInfoById(id).get() }
      ?: return WorkoutAiTaskSnapshot(
        state = WorkoutAiTaskState.FAILED,
        error = "AIタスクが見つかりません",
      )
    return when (info.state) {
      WorkInfo.State.ENQUEUED,
      WorkInfo.State.BLOCKED -> WorkoutAiTaskSnapshot(WorkoutAiTaskState.QUEUED)
      WorkInfo.State.RUNNING -> WorkoutAiTaskSnapshot(WorkoutAiTaskState.RUNNING)
      WorkInfo.State.SUCCEEDED -> WorkoutAiTaskSnapshot(
        state = WorkoutAiTaskState.SUCCEEDED,
        response = info.outputData.getString(KEY_RESPONSE),
      )
      WorkInfo.State.CANCELLED -> WorkoutAiTaskSnapshot(WorkoutAiTaskState.CANCELLED)
      WorkInfo.State.FAILED -> WorkoutAiTaskSnapshot(
        state = WorkoutAiTaskState.FAILED,
        error = info.outputData.getString(KEY_ERROR),
      )
    }
  }
}

class WorkoutAiWorker(
  appContext: Context,
  params: WorkerParameters,
  private val workoutReader: WorkoutReader,
  private val settingsRepository: WorkoutAiSettingsRepository,
  private val advisor: WorkoutAiAdvisor,
) : CoroutineWorker(appContext, params) {
  override suspend fun doWork(): Result = withAiBackgroundInference {
    val type = inputData.getString(KEY_REQUEST_TYPE)
      ?.let { value -> WorkoutAiRequestType.entries.firstOrNull { it.name == value } }
      ?: return@withAiBackgroundInference Result.failure()
    val provider = inputData.getString(KEY_PROVIDER)
      ?.let { value -> WorkoutAiProvider.entries.firstOrNull { it.name == value } }
      ?: return@withAiBackgroundInference Result.failure()

    if (isPaused(provider)) return@withAiBackgroundInference Result.retry()
    setForeground(createForegroundInfo(type))

    try {
      val response = when (provider) {
        WorkoutAiProvider.LOCAL -> LocalAiBackgroundTaskGate.withPermit {
          if (isPaused(provider)) return@withPermit null
          generate(type, provider)
        } ?: return@withAiBackgroundInference Result.retry()
        WorkoutAiProvider.CHATGPT -> {
          if (isPaused(provider)) return@withAiBackgroundInference Result.retry()
          generate(type, provider)
        }
      }
      Result.success(workDataOf(KEY_RESPONSE to response.take(MAX_RESPONSE_CHARS)))
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (error: Throwable) {
      Result.failure(workDataOf(KEY_ERROR to safeErrorMessage(error)))
    }
  }

  private suspend fun generate(
    type: WorkoutAiRequestType,
    provider: WorkoutAiProvider,
  ): String {
    val snapshot = workoutReader.load()
    val settings = settingsRepository.loadSettings().copy(provider = provider)
    val dates = WorkoutAiPromptBuilder.recentDates(snapshot)
    val memos = settingsRepository.loadMemos(dates)
    val prompt = WorkoutAiPromptBuilder.build(
      type = type,
      snapshot = snapshot,
      settings = settings,
      memos = memos,
    )
    return advisor.generate(provider, prompt).trim().ifBlank { "応答が空でした" }
  }

  private fun isPaused(provider: WorkoutAiProvider): Boolean = when (provider) {
    WorkoutAiProvider.LOCAL -> LocalAiBackgroundExecutionPreferences(applicationContext).paused
    WorkoutAiProvider.CHATGPT -> CloudAiBackgroundExecutionPreferences(applicationContext).paused
  }

  private fun createForegroundInfo(type: WorkoutAiRequestType): ForegroundInfo {
    val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
    notificationManager.createNotificationChannel(
      NotificationChannel(CHANNEL_ID, "ワークアウトAI", NotificationManager.IMPORTANCE_LOW).apply {
        description = "ワークアウトのAI支援をバックグラウンドで実行している間に表示します"
        setShowBadge(false)
      },
    )
    val title = when (type) {
      WorkoutAiRequestType.MENU_SUGGESTION -> "今日のメニューを生成しています"
      WorkoutAiRequestType.POST_WORKOUT_REVIEW -> "ワークアウトをレビューしています"
    }
    val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.stat_notify_sync)
      .setContentTitle("ワークアウトAIを実行中")
      .setContentText(title)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setCategory(NotificationCompat.CATEGORY_PROGRESS)
      .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
    applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)?.let { intent ->
      PendingIntent.getActivity(
        applicationContext,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    }?.let(builder::setContentIntent)
    return ForegroundInfo(
      NOTIFICATION_ID,
      builder.build(),
      ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
    )
  }

  private fun safeErrorMessage(error: Throwable): String =
    generateSequence(error) { it.cause }
      .mapNotNull(Throwable::message)
      .firstOrNull(String::isNotBlank)
      ?.take(MAX_ERROR_CHARS)
      ?: "AIの応答生成に失敗しました"

  private companion object {
    const val CHANNEL_ID = "workout_ai_generation"
    const val NOTIFICATION_ID = 12_500
  }
}

class WorkoutAiWorkerFactory(
  private val workoutReaderProvider: () -> WorkoutReader,
  private val settingsRepositoryProvider: () -> WorkoutAiSettingsRepository,
  private val advisorProvider: () -> WorkoutAiAdvisor,
) : WorkerFactory() {
  override fun createWorker(
    appContext: Context,
    workerClassName: String,
    workerParameters: WorkerParameters,
  ): ListenableWorker? = if (workerClassName == WorkoutAiWorker::class.java.name) {
    WorkoutAiWorker(
      appContext = appContext,
      params = workerParameters,
      workoutReader = workoutReaderProvider(),
      settingsRepository = settingsRepositoryProvider(),
      advisor = advisorProvider(),
    )
  } else {
    null
  }
}

private const val WORK_NAME_PREFIX = "workout-ai-"
private const val KEY_REQUEST_TYPE = "workout_ai_request_type"
private const val KEY_PROVIDER = "workout_ai_provider"
private const val KEY_RESPONSE = "workout_ai_response"
private const val KEY_ERROR = "workout_ai_error"
private const val MAX_RESPONSE_CHARS = 8_000
private const val MAX_ERROR_CHARS = 500
