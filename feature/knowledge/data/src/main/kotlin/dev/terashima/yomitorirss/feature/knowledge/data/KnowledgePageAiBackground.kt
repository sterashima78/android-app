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
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import androidx.work.workDataOf
import dev.terashima.yomitorirss.core.aiinference.withAiBackgroundInference
import dev.terashima.yomitorirss.core.background.CloudAiBackgroundExecutionPreferences
import dev.terashima.yomitorirss.core.background.LocalAiBackgroundExecutionPreferences
import dev.terashima.yomitorirss.core.background.LocalAiBackgroundTaskGate
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeCloudInferenceException
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeExecutionProvider
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeExecutionSettings
import dev.terashima.yomitorirss.feature.knowledge.KnowledgePageAiRunner
import dev.terashima.yomitorirss.feature.knowledge.KnowledgePageAiTaskController
import dev.terashima.yomitorirss.feature.knowledge.KnowledgePageAiTaskSnapshot
import dev.terashima.yomitorirss.feature.knowledge.KnowledgePageAiTaskState
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

class WorkManagerKnowledgePageAiTaskController(
  context: Context,
  private val executionSettings: KnowledgeExecutionSettings,
) : KnowledgePageAiTaskController {
  private val appContext = context.applicationContext
  private val workManager = WorkManager.getInstance(appContext)
  private val requestStore = KnowledgePageAiRequestStore(appContext)

  override suspend fun enqueueCreate(
    request: String,
    sourcePageId: String?,
  ): String {
    val normalized = request.trim()
    require(normalized.isNotBlank()) { "作成したい記事の内容を入力してください" }
    return enqueue(
      operation = KnowledgePageAiOperation.CREATE,
      payload = KnowledgePageAiRequest(
        request = normalized,
        sourcePageId = sourcePageId,
      ),
    )
  }

  override suspend fun enqueueEdit(
    pageId: String,
    instruction: String,
  ): String {
    val normalized = instruction.trim()
    require(pageId.isNotBlank()) { "編集するナレッジページが見つかりません" }
    require(normalized.isNotBlank()) { "編集内容を入力してください" }
    return enqueue(
      operation = KnowledgePageAiOperation.EDIT,
      payload = KnowledgePageAiRequest(
        pageId = pageId,
        instruction = normalized,
      ),
    )
  }

  override suspend fun snapshot(requestId: String): KnowledgePageAiTaskSnapshot {
    val uuid = runCatching(UUID::fromString).getOrNull(requestId)
      ?: return KnowledgePageAiTaskSnapshot(
        state = KnowledgePageAiTaskState.FAILED,
        error = "Knowledge AIタスクIDが不正です",
      )
    val info = workManager.getWorkInfoById(uuid).await()
      ?: return KnowledgePageAiTaskSnapshot(
        state = KnowledgePageAiTaskState.FAILED,
        error = "Knowledge AIタスクが見つかりません",
      )
    return when (info.state) {
      WorkInfo.State.ENQUEUED,
      WorkInfo.State.BLOCKED -> KnowledgePageAiTaskSnapshot(KnowledgePageAiTaskState.QUEUED)
      WorkInfo.State.RUNNING -> KnowledgePageAiTaskSnapshot(KnowledgePageAiTaskState.RUNNING)
      WorkInfo.State.SUCCEEDED -> KnowledgePageAiTaskSnapshot(
        state = KnowledgePageAiTaskState.SUCCEEDED,
        pageId = info.outputData.getString(KEY_PAGE_ID),
      )
      WorkInfo.State.FAILED -> KnowledgePageAiTaskSnapshot(
        state = KnowledgePageAiTaskState.FAILED,
        error = info.outputData.getString(KEY_ERROR),
      )
      WorkInfo.State.CANCELLED -> KnowledgePageAiTaskSnapshot(KnowledgePageAiTaskState.CANCELLED)
    }
  }

  private suspend fun enqueue(
    operation: KnowledgePageAiOperation,
    payload: KnowledgePageAiRequest,
  ): String {
    requestStore.deleteExpired()
    val provider = executionSettings.currentProvider()
    val builder = OneTimeWorkRequestBuilder<KnowledgePageAiWorker>()
      .setInputData(
        workDataOf(
          KEY_OPERATION to operation.name,
          KEY_PROVIDER to provider.name,
        ),
      )
      .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
    if (provider == KnowledgeExecutionProvider.CHATGPT) {
      builder.setConstraints(
        Constraints.Builder()
          .setRequiredNetworkType(NetworkType.CONNECTED)
          .build(),
      )
    }
    val request = builder.build()
    requestStore.write(request.id.toString(), payload)
    try {
      workManager.enqueueUniqueWork(
        "$WORK_NAME_PREFIX${request.id}",
        ExistingWorkPolicy.KEEP,
        request,
      ).await()
    } catch (error: Throwable) {
      requestStore.delete(request.id.toString())
      throw error
    }
    return request.id.toString()
  }
}

class KnowledgePageAiWorker(
  appContext: Context,
  params: WorkerParameters,
  private val runner: KnowledgePageAiRunner,
) : CoroutineWorker(appContext, params) {
  private val requestStore = KnowledgePageAiRequestStore(appContext)

  override suspend fun doWork(): Result = withAiBackgroundInference {
    val provider = inputData.getString(KEY_PROVIDER)
      ?.let { saved -> KnowledgeExecutionProvider.entries.firstOrNull { it.name == saved } }
      ?: return@withAiBackgroundInference Result.failure()
    val operation = inputData.getString(KEY_OPERATION)
      ?.let { saved -> KnowledgePageAiOperation.entries.firstOrNull { it.name == saved } }
      ?: return@withAiBackgroundInference Result.failure()
    val requestId = id.toString()
    val payload = requestStore.read(requestId)
      ?: return@withAiBackgroundInference Result.failure(
        workDataOf(KEY_ERROR to "Knowledge AIタスクの入力が見つかりません"),
      )

    if (isKnowledgeProviderPaused(applicationContext, provider)) {
      return@withAiBackgroundInference Result.retry()
    }
    setForeground(createForegroundInfo(operation))

    try {
      val page = when (provider) {
        KnowledgeExecutionProvider.LOCAL -> LocalAiBackgroundTaskGate.withPermit {
          if (isKnowledgeProviderPaused(applicationContext, provider)) return@withPermit null
          execute(operation, provider, payload)
        } ?: return@withAiBackgroundInference Result.retry()
        KnowledgeExecutionProvider.CHATGPT -> {
          if (CloudAiBackgroundExecutionPreferences(applicationContext).paused) {
            return@withAiBackgroundInference Result.retry()
          }
          execute(operation, provider, payload)
        }
      }
      requestStore.delete(requestId)
      Result.success(workDataOf(KEY_PAGE_ID to page.id))
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (error: KnowledgeCloudInferenceException) {
      if (provider == KnowledgeExecutionProvider.CHATGPT && error.retryable) {
        Result.retry()
      } else {
        requestStore.delete(requestId)
        Result.failure(workDataOf(KEY_ERROR to error.userMessage()))
      }
    } catch (error: Throwable) {
      requestStore.delete(requestId)
      Result.failure(workDataOf(KEY_ERROR to error.userMessage()))
    }
  }

  private suspend fun execute(
    operation: KnowledgePageAiOperation,
    provider: KnowledgeExecutionProvider,
    payload: KnowledgePageAiRequest,
  ) = when (operation) {
    KnowledgePageAiOperation.CREATE -> runner.createPage(
      provider = provider,
      request = requireNotNull(payload.request),
      sourcePageId = payload.sourcePageId,
    )
    KnowledgePageAiOperation.EDIT -> runner.editPage(
      provider = provider,
      pageId = requireNotNull(payload.pageId),
      instruction = requireNotNull(payload.instruction),
    )
  }

  private fun createForegroundInfo(operation: KnowledgePageAiOperation): ForegroundInfo {
    val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
    notificationManager.createNotificationChannel(
      NotificationChannel(CHANNEL_ID, "Knowledge AI編集", NotificationManager.IMPORTANCE_LOW).apply {
        description = "Knowledgeページをバックグラウンドで生成・編集している間に表示します"
        setShowBadge(false)
      },
    )
    val text = when (operation) {
      KnowledgePageAiOperation.CREATE -> "Knowledgeページを生成しています"
      KnowledgePageAiOperation.EDIT -> "Knowledgeページを編集しています"
    }
    val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.stat_notify_sync)
      .setContentTitle("Knowledge AIを実行中")
      .setContentText(text)
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
      NOTIFICATION_ID_BASE + (id.hashCode() and 0x3fff),
      builder.build(),
      ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
    )
  }
}

private enum class KnowledgePageAiOperation {
  CREATE,
  EDIT,
}

private data class KnowledgePageAiRequest(
  val request: String? = null,
  val sourcePageId: String? = null,
  val pageId: String? = null,
  val instruction: String? = null,
)

private class KnowledgePageAiRequestStore(context: Context) {
  private val directory = File(context.noBackupFilesDir, DIRECTORY_NAME).apply { mkdirs() }

  fun write(id: String, request: KnowledgePageAiRequest) {
    val json = JSONObject().apply {
      request.request?.let { put("request", it) }
      request.sourcePageId?.let { put("sourcePageId", it) }
      request.pageId?.let { put("pageId", it) }
      request.instruction?.let { put("instruction", it) }
      put("createdAt", System.currentTimeMillis())
    }
    file(id).writeText(json.toString())
  }

  fun read(id: String): KnowledgePageAiRequest? = runCatching {
    val json = JSONObject(file(id).readText())
    KnowledgePageAiRequest(
      request = json.optString("request").takeIf(String::isNotBlank),
      sourcePageId = json.optString("sourcePageId").takeIf(String::isNotBlank),
      pageId = json.optString("pageId").takeIf(String::isNotBlank),
      instruction = json.optString("instruction").takeIf(String::isNotBlank),
    )
  }.getOrNull()

  fun delete(id: String) {
    file(id).delete()
  }

  fun deleteExpired(nowMillis: Long = System.currentTimeMillis()) {
    directory.listFiles()?.forEach { candidate ->
      if (nowMillis - candidate.lastModified() > REQUEST_RETENTION_MILLIS) candidate.delete()
    }
  }

  private fun file(id: String): File = File(directory, "$id.json")

  private companion object {
    const val DIRECTORY_NAME = "knowledge-page-ai-requests"
    const val REQUEST_RETENTION_MILLIS = 7L * 24 * 60 * 60 * 1_000
  }
}

private fun Throwable.userMessage(): String =
  generateSequence(this) { it.cause }
    .mapNotNull(Throwable::message)
    .firstOrNull(String::isNotBlank)
    ?.take(MAX_ERROR_CHARS)
    ?: javaClass.simpleName

private const val WORK_NAME_PREFIX = "knowledge-page-ai-"
private const val KEY_OPERATION = "knowledge_page_ai_operation"
private const val KEY_PROVIDER = "knowledge_page_ai_provider"
private const val KEY_PAGE_ID = "knowledge_page_ai_page_id"
private const val KEY_ERROR = "knowledge_page_ai_error"
private const val CHANNEL_ID = "knowledge_page_ai"
private const val NOTIFICATION_ID_BASE = 13_000
private const val MAX_ERROR_CHARS = 500
