package dev.terashima.yomitorirss.feature.knowledge.data

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
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.await
import androidx.work.workDataOf
import dev.terashima.yomitorirss.core.background.CloudAiBackgroundExecutionPreferences
import dev.terashima.yomitorirss.core.background.LocalAiBackgroundExecutionPreferences
import dev.terashima.yomitorirss.core.background.LocalAiBackgroundTaskGate
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeBuildRunner
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeBuildTaskController
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeBuildTaskSnapshot
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeBuildTaskState
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeCloudInferenceException
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeExecutionProvider
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeExecutionSettings
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WorkManagerKnowledgeBuildTaskController(
  context: Context,
  private val executionSettings: KnowledgeExecutionSettings,
) : KnowledgeBuildTaskController {
  private val appContext = context.applicationContext
  private val state = KnowledgeBuildQueueStateStore(appContext)
  private val workManager = WorkManager.getInstance(appContext)

  fun enqueue() {
    state.request()
    workManager.cancelAllWorkByTag(WORK_TAG)
    kick(forceReschedule = true)
  }

  override fun kick() {
    kick(forceReschedule = false)
  }

  fun onProviderChanged() {
    if (!state.requested || state.stopped || state.failed) return
    val requestId = state.request()
    val provider = executionSettings.currentProvider()
    workManager.cancelAllWorkByTag(WORK_TAG)
    if (isKnowledgeProviderPaused(appContext, provider)) {
      setResumeOnChargingScheduled(provider == KnowledgeExecutionProvider.LOCAL)
      return
    }
    setResumeOnChargingScheduled(false)
    enqueueBuildWork(ExistingWorkPolicy.REPLACE, provider, requestId)
  }

  private fun kick(forceReschedule: Boolean) {
    if (!state.requested || state.stopped || state.failed) return
    if (shouldSkipKnowledgeBuildKick(forceReschedule, state.hasPendingTopics)) return
    val requestId = state.ensureRequestId() ?: return
    val provider = executionSettings.currentProvider()
    if (isKnowledgeProviderPaused(appContext, provider)) {
      setResumeOnChargingScheduled(provider == KnowledgeExecutionProvider.LOCAL)
      return
    }
    setResumeOnChargingScheduled(false)
    enqueueBuildWork(knowledgeBuildExistingWorkPolicy(forceReschedule), provider, requestId)
  }

  override suspend fun pauseForGlobalGate() {
    if (!state.requested) return
    workManager.cancelAllWorkByTag(WORK_TAG).await()
    state.clearPlannedTopics()
  }

  override suspend fun stop(): Boolean {
    if (!state.requested || state.stopped) return false
    state.markStopped()
    workManager.cancelAllWorkByTag(WORK_TAG).await()
    return true
  }

  override suspend fun cancel(): Boolean {
    if (!state.requested) return false
    state.clear()
    workManager.cancelAllWorkByTag(WORK_TAG).await()
    workManager.cancelUniqueWork(RESUME_ON_CHARGING_WORK_NAME).await()
    return true
  }

  override suspend fun resume(): Boolean {
    if (!state.requested || (!state.stopped && !state.failed)) return false
    state.markReady()
    kick()
    return true
  }

  override suspend fun snapshot(): KnowledgeBuildTaskSnapshot? {
    if (!state.requested) return null
    if (isKnowledgeProviderPaused(appContext, executionSettings.currentProvider())) {
      return KnowledgeBuildTaskSnapshot(KnowledgeBuildTaskState.PAUSED)
    }
    if (state.stopped) return KnowledgeBuildTaskSnapshot(KnowledgeBuildTaskState.STOPPED)
    if (state.failed) {
      return KnowledgeBuildTaskSnapshot(
        state = KnowledgeBuildTaskState.FAILED,
        error = state.error,
      )
    }

    return KnowledgeBuildTaskSnapshot(
      state = knowledgeBuildTaskState(state.hasPendingTopics),
      error = state.error,
    )
  }

  override fun setResumeOnChargingScheduled(enabled: Boolean) {
    if (!enabled) {
      workManager.cancelUniqueWork(RESUME_ON_CHARGING_WORK_NAME)
      return
    }

    val execution = LocalAiBackgroundExecutionPreferences(appContext)
    if (
      !state.requested ||
      executionSettings.currentProvider() != KnowledgeExecutionProvider.LOCAL ||
      !execution.paused ||
      !execution.resumeWhenCharging
    ) {
      return
    }
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
    if (executionSettings.currentProvider() != KnowledgeExecutionProvider.LOCAL) return
    kick(forceReschedule = false)
  }

  private fun enqueueBuildWork(
    policy: ExistingWorkPolicy,
    provider: KnowledgeExecutionProvider,
    requestId: String,
  ) {
    val request = OneTimeWorkRequestBuilder<KnowledgeBuildWorker>()
      .addTag(WORK_TAG)
      .setInputData(
        workDataOf(
          KNOWLEDGE_EXECUTION_PROVIDER_KEY to provider.name,
          KNOWLEDGE_REQUEST_ID_KEY to requestId,
        ),
      )
      .build()
    workManager.enqueueUniqueWork(WORK_NAME, policy, request)
  }

  companion object {
    internal const val WORK_NAME = "knowledge-ai-wiki-build"
    internal const val WORK_TAG = "knowledge-ai-wiki"
    private const val RESUME_ON_CHARGING_WORK_NAME = "knowledge-ai-wiki-resume-on-charging"
  }
}

internal fun knowledgeBuildExistingWorkPolicy(forceReschedule: Boolean): ExistingWorkPolicy =
  if (forceReschedule) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP

internal fun shouldSkipKnowledgeBuildKick(
  forceReschedule: Boolean,
  hasPendingTopics: Boolean,
): Boolean = !forceReschedule && hasPendingTopics

internal fun knowledgeBuildTaskState(hasPendingTopics: Boolean): KnowledgeBuildTaskState =
  if (hasPendingTopics) KnowledgeBuildTaskState.RUNNING else KnowledgeBuildTaskState.QUEUED

internal fun isKnowledgeProviderPaused(
  context: Context,
  provider: KnowledgeExecutionProvider,
): Boolean = when (provider) {
  KnowledgeExecutionProvider.LOCAL -> LocalAiBackgroundExecutionPreferences(context).paused
  KnowledgeExecutionProvider.CHATGPT -> CloudAiBackgroundExecutionPreferences(context).paused
}

internal class KnowledgeBuildResumeOnChargingWorker(
  appContext: Context,
  params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
  override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
    val execution = LocalAiBackgroundExecutionPreferences(applicationContext)
    if (!execution.resumeWhenCharging) return@withContext Result.success()

    execution.paused = false
    WorkManagerKnowledgeBuildTaskController(
      applicationContext,
      KnowledgeExecutionPreferences(applicationContext),
    ).kickFromChargingResume()
    Result.success()
  }
}

/** Plans a rebuild from local saved-summary state and enqueues one durable WorkRequest per changed topic. */
internal class KnowledgeBuildWorker(
  appContext: Context,
  params: WorkerParameters,
  private val knowledgeBuilder: KnowledgeBuildRunner,
) : CoroutineWorker(appContext, params) {
  override suspend fun doWork(): Result {
    val state = KnowledgeBuildQueueStateStore(applicationContext)
    val requestId = inputData.getString(KNOWLEDGE_REQUEST_ID_KEY)
      ?: state.ensureRequestId()
      ?: return Result.success()
    if (!state.isActive(requestId)) return Result.success()
    val provider = inputData.executionProvider()
    if (isKnowledgeProviderPaused(applicationContext, provider)) return Result.success()

    return try {
      val plan = knowledgeBuilder.planRebuild(provider)
      if (!state.isActive(requestId)) return Result.success()
      if (!state.setPlannedTopics(requestId, plan.topicIds)) return Result.success()
      if (plan.topicIds.isEmpty()) {
        state.complete(requestId)
        return Result.success()
      }

      WorkManager.getInstance(applicationContext).enqueue(
        plan.topicIds.map { topicId ->
          knowledgeTopicWorkRequest(provider, requestId, topicId)
        },
      ).await()
      Result.success()
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (error: Throwable) {
      state.markFailed(requestId, error.userMessage())
      Result.failure()
    }
  }
}

/** Generates or refreshes exactly one planned Wiki topic. */
internal class KnowledgeTopicBuildWorker(
  appContext: Context,
  params: WorkerParameters,
  private val knowledgeBuilder: KnowledgeBuildRunner,
) : CoroutineWorker(appContext, params) {
  override suspend fun doWork(): Result {
    val state = KnowledgeBuildQueueStateStore(applicationContext)
    val requestId = inputData.getString(KNOWLEDGE_REQUEST_ID_KEY) ?: return Result.failure()
    val topicId = inputData.getString(KNOWLEDGE_TOPIC_ID_KEY) ?: return Result.failure()
    val provider = inputData.executionProvider()
    if (!state.isActive(requestId)) return Result.success()
    if (isKnowledgeProviderPaused(applicationContext, provider)) return Result.retry()

    return try {
      when (provider) {
        KnowledgeExecutionProvider.LOCAL -> LocalAiBackgroundTaskGate.withPermit {
          setForeground(
            createKnowledgeBuildForegroundInfo(
              context = applicationContext,
              notificationId = KNOWLEDGE_NOTIFICATION_BASE_ID + (id.hashCode() and 0x3fff),
            ),
          )
          runTopic(state, requestId, provider, topicId)
        }
        KnowledgeExecutionProvider.CHATGPT -> {
          setForeground(
            createKnowledgeBuildForegroundInfo(
              context = applicationContext,
              notificationId = KNOWLEDGE_NOTIFICATION_BASE_ID + (id.hashCode() and 0x3fff),
            ),
          )
          runTopic(state, requestId, provider, topicId)
        }
      }
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (error: KnowledgeCloudInferenceException) {
      if (provider == KnowledgeExecutionProvider.CHATGPT && error.retryable) {
        state.markRetrying(requestId, error.userMessage())
        Result.retry()
      } else {
        state.markFailed(requestId, error.userMessage())
        Result.failure()
      }
    } catch (error: Throwable) {
      state.markFailed(requestId, error.userMessage())
      Result.failure()
    }
  }

  private suspend fun runTopic(
    state: KnowledgeBuildQueueStateStore,
    requestId: String,
    provider: KnowledgeExecutionProvider,
    topicId: String,
  ): Result {
    if (!state.isActive(requestId)) return Result.success()
    if (isKnowledgeProviderPaused(applicationContext, provider)) return Result.retry()
    knowledgeBuilder.rebuildTopic(provider, topicId)
    state.markTopicCompleted(requestId, topicId)
    return Result.success()
  }
}

internal fun knowledgeTopicWorkRequest(
  provider: KnowledgeExecutionProvider,
  requestId: String,
  topicId: String,
): OneTimeWorkRequest {
  val builder = OneTimeWorkRequestBuilder<KnowledgeTopicBuildWorker>()
    .addTag(WorkManagerKnowledgeBuildTaskController.WORK_TAG)
    .setInputData(
      workDataOf(
        KNOWLEDGE_EXECUTION_PROVIDER_KEY to provider.name,
        KNOWLEDGE_REQUEST_ID_KEY to requestId,
        KNOWLEDGE_TOPIC_ID_KEY to topicId,
      ),
    )
  if (provider == KnowledgeExecutionProvider.CHATGPT) {
    builder
      .setConstraints(
        Constraints.Builder()
          .setRequiredNetworkType(NetworkType.CONNECTED)
          .build(),
      )
      .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
  }
  return builder.build()
}

private fun createKnowledgeBuildForegroundInfo(
  context: Context,
  notificationId: Int,
): ForegroundInfo {
  val notificationManager = context.getSystemService(NotificationManager::class.java)
  notificationManager.createNotificationChannel(
    NotificationChannel(
      KNOWLEDGE_NOTIFICATION_CHANNEL_ID,
      "LLM Wiki生成",
      NotificationManager.IMPORTANCE_LOW,
    ).apply {
      description = "AIでLLM Wikiをバックグラウンド生成している間に表示します"
      setShowBadge(false)
    },
  )

  val notificationBuilder = NotificationCompat.Builder(context, KNOWLEDGE_NOTIFICATION_CHANNEL_ID)
    .setSmallIcon(android.R.drawable.stat_notify_sync)
    .setContentTitle("LLM Wikiを構築しています")
    .setContentText("保存済み要約からWikiを更新しています")
    .setOngoing(true)
    .setOnlyAlertOnce(true)
    .setCategory(NotificationCompat.CATEGORY_PROGRESS)
    .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

  context.packageManager
    .getLaunchIntentForPackage(context.packageName)
    ?.let { launchIntent ->
      PendingIntent.getActivity(
        context,
        0,
        launchIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    }
    ?.let(notificationBuilder::setContentIntent)

  return ForegroundInfo(
    notificationId,
    notificationBuilder.build(),
    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
  )
}

class KnowledgeWorkerFactory(
  private val knowledgeBuilderProvider: () -> KnowledgeBuildRunner,
) : WorkerFactory() {
  override fun createWorker(
    appContext: Context,
    workerClassName: String,
    workerParameters: WorkerParameters,
  ): ListenableWorker? = when (workerClassName) {
    KnowledgeBuildWorker::class.java.name ->
      KnowledgeBuildWorker(appContext, workerParameters, knowledgeBuilderProvider())
    KnowledgeTopicBuildWorker::class.java.name ->
      KnowledgeTopicBuildWorker(appContext, workerParameters, knowledgeBuilderProvider())
    else -> null
  }
}

private fun androidx.work.Data.executionProvider(): KnowledgeExecutionProvider =
  getString(KNOWLEDGE_EXECUTION_PROVIDER_KEY)
    ?.let { saved -> KnowledgeExecutionProvider.entries.firstOrNull { it.name == saved } }
    ?: KnowledgeExecutionProvider.LOCAL

private fun Throwable.userMessage(): String =
  generateSequence(this) { it.cause }
    .mapNotNull(Throwable::message)
    .firstOrNull(String::isNotBlank)
    ?: javaClass.simpleName

private const val KNOWLEDGE_EXECUTION_PROVIDER_KEY = "knowledge_execution_provider"
private const val KNOWLEDGE_REQUEST_ID_KEY = "knowledge_request_id"
private const val KNOWLEDGE_TOPIC_ID_KEY = "knowledge_topic_id"
private const val KNOWLEDGE_NOTIFICATION_CHANNEL_ID = "knowledge_ai_generation"
private const val KNOWLEDGE_NOTIFICATION_BASE_ID = 8770
