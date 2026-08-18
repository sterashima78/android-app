package dev.terashima.yomitorirss.feature.knowledge.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import dev.terashima.yomitorirss.core.background.LocalAiBackgroundExecutionPreferences
import dev.terashima.yomitorirss.core.background.LocalAiBackgroundTaskGate
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeRepositoryProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class KnowledgeBuildTaskState {
  QUEUED,
  RUNNING,
  PAUSED,
  STOPPED,
  FAILED,
}

data class KnowledgeBuildTaskSnapshot(
  val state: KnowledgeBuildTaskState,
  val error: String? = null,
)

class WorkManagerKnowledgeBuildTaskController(
  context: Context,
) {
  private val appContext = context.applicationContext
  private val state = KnowledgeBuildQueueStateStore(appContext)
  private val workManager = WorkManager.getInstance(appContext)

  fun enqueue() {
    state.request()
    kick()
  }

  fun kick() {
    if (!state.requested || state.stopped || state.failed) return
    val execution = LocalAiBackgroundExecutionPreferences(appContext)
    if (execution.paused) {
      setResumeOnChargingScheduled(true)
      return
    }
    setResumeOnChargingScheduled(false)
    enqueueBuildWork()
  }

  suspend fun pauseForGlobalGate() {
    if (!state.requested) return
    workManager.cancelUniqueWork(WORK_NAME).await()
  }

  suspend fun stop(): Boolean {
    if (!state.requested || state.stopped) return false
    state.markStopped()
    workManager.cancelUniqueWork(WORK_NAME).await()
    return true
  }

  suspend fun cancel(): Boolean {
    if (!state.requested) return false
    state.clear()
    workManager.cancelUniqueWork(WORK_NAME).await()
    workManager.cancelUniqueWork(RESUME_ON_CHARGING_WORK_NAME).await()
    return true
  }

  suspend fun resume(): Boolean {
    if (!state.requested || (!state.stopped && !state.failed)) return false
    state.markReady()
    kick()
    return true
  }

  suspend fun snapshot(): KnowledgeBuildTaskSnapshot? {
    if (!state.requested) return null
    if (LocalAiBackgroundExecutionPreferences(appContext).paused) {
      return KnowledgeBuildTaskSnapshot(KnowledgeBuildTaskState.PAUSED)
    }
    if (state.stopped) return KnowledgeBuildTaskSnapshot(KnowledgeBuildTaskState.STOPPED)
    if (state.failed) {
      return KnowledgeBuildTaskSnapshot(
        state = KnowledgeBuildTaskState.FAILED,
        error = state.error,
      )
    }

    val workInfos = withContext(Dispatchers.IO) {
      workManager.getWorkInfosForUniqueWork(WORK_NAME).get()
    }
    val workState = when {
      workInfos.any { it.state == WorkInfo.State.RUNNING } -> KnowledgeBuildTaskState.RUNNING
      workInfos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED } ->
        KnowledgeBuildTaskState.QUEUED
      else -> KnowledgeBuildTaskState.QUEUED
    }
    return KnowledgeBuildTaskSnapshot(workState)
  }

  fun setResumeOnChargingScheduled(enabled: Boolean) {
    if (!enabled) {
      workManager.cancelUniqueWork(RESUME_ON_CHARGING_WORK_NAME)
      return
    }

    val execution = LocalAiBackgroundExecutionPreferences(appContext)
    if (!state.requested || !execution.paused || !execution.resumeWhenCharging) return
    val request = OneTimeWorkRequestBuilder<KnowledgeBuildResumeOnChargingWorker>()
      .setConstraints(
        Constraints.Builder()
          .setRequiresCharging(true)
          .build(),
      )
      .build()
    workManager.enqueueUniqueWork(
      RESUME_ON_CHARGING_WORK_NAME,
      ExistingWorkPolicy.KEEP,
      request,
    )
  }

  internal fun kickFromChargingResume() {
    if (!state.requested || state.stopped || state.failed) return
    enqueueBuildWork()
  }

  private fun enqueueBuildWork() {
    val request = OneTimeWorkRequestBuilder<KnowledgeBuildWorker>()
      .addTag(WORK_TAG)
      .build()
    workManager.enqueueUniqueWork(
      WORK_NAME,
      ExistingWorkPolicy.KEEP,
      request,
    )
  }

  companion object {
    internal const val WORK_NAME = "knowledge-ai-wiki-build"
    internal const val WORK_TAG = "knowledge-ai-wiki"
    private const val RESUME_ON_CHARGING_WORK_NAME = "knowledge-ai-wiki-resume-on-charging"
  }
}

internal class KnowledgeBuildResumeOnChargingWorker(
  appContext: Context,
  params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
  override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
    val execution = LocalAiBackgroundExecutionPreferences(applicationContext)
    if (!execution.resumeWhenCharging) return@withContext Result.success()

    execution.paused = false
    WorkManagerKnowledgeBuildTaskController(applicationContext).kickFromChargingResume()
    Result.success()
  }
}

internal class KnowledgeBuildWorker(
  appContext: Context,
  params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
  override suspend fun doWork(): Result {
    val state = KnowledgeBuildQueueStateStore(applicationContext)
    if (!state.requested || state.stopped) return Result.success()
    if (LocalAiBackgroundExecutionPreferences(applicationContext).paused) return Result.success()

    return try {
      setForeground(createForegroundInfo())
      LocalAiBackgroundTaskGate.withPermit {
        if (!state.requested || state.stopped) return@withPermit Result.success()
        if (LocalAiBackgroundExecutionPreferences(applicationContext).paused) {
          return@withPermit Result.success()
        }

        val provider = applicationContext as? KnowledgeRepositoryProvider
          ?: error("ナレッジリポジトリの初期化状態を取得できませんでした")
        provider.knowledgeRepository.rebuild()
        state.complete()
        Result.success()
      }
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (error: Throwable) {
      state.markFailed(error.userMessage())
      Result.failure()
    }
  }

  private fun createForegroundInfo(): ForegroundInfo {
    val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
    notificationManager.createNotificationChannel(
      NotificationChannel(
        CHANNEL_ID,
        "LLM Wiki生成",
        NotificationManager.IMPORTANCE_LOW,
      ).apply {
        description = "ローカルAIでLLM Wikiをバックグラウンド生成している間に表示します"
        setShowBadge(false)
      },
    )

    val notificationBuilder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.stat_notify_sync)
      .setContentTitle("LLM Wikiを構築しています")
      .setContentText("保存済み要約からWikiを更新しています")
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setCategory(NotificationCompat.CATEGORY_PROGRESS)
      .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

    applicationContext.packageManager
      .getLaunchIntentForPackage(applicationContext.packageName)
      ?.let { launchIntent ->
        PendingIntent.getActivity(
          applicationContext,
          0,
          launchIntent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
      }
      ?.let(notificationBuilder::setContentIntent)

    val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
    } else {
      0
    }
    return ForegroundInfo(NOTIFICATION_ID, notificationBuilder.build(), serviceType)
  }

  private companion object {
    const val CHANNEL_ID = "knowledge_ai_generation"
    const val NOTIFICATION_ID = 8770
  }
}

private fun Throwable.userMessage(): String =
  generateSequence(this) { it.cause }
    .mapNotNull(Throwable::message)
    .firstOrNull(String::isNotBlank)
    ?: javaClass.simpleName
