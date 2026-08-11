package dev.terashima.yomitorirss.feature.settings.data

import android.annotation.SuppressLint
import android.app.JobInfo
import android.app.JobParameters
import android.app.JobScheduler
import android.app.JobService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.PersistableBundle
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.terashima.yomitorirss.core.airuntime.LocalModelManager
import dev.terashima.yomitorirss.core.airuntime.ModelDownloadProgress
import dev.terashima.yomitorirss.feature.settings.AiModelDownloadProgress
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

private const val INPUT_MODEL_ID = "ai_model_download_model_id"
private const val INPUT_MODEL_SIZE_BYTES = "ai_model_download_model_size_bytes"
private const val WORK_NAME = "ai-model-download"
private const val JOB_ID = 0x594f4d4f
private const val NOTIFICATION_CHANNEL_ID = "ai-model-download"
private const val NOTIFICATION_ID = 0x41494d44
private const val DOWNLOAD_STATE_PREFERENCES = "ai_model_download_state"
private const val KEY_MODEL_ID = "model_id"
private const val KEY_PHASE = "phase"
private const val KEY_DOWNLOADED_BYTES = "downloaded_bytes"
private const val KEY_TOTAL_BYTES = "total_bytes"
private const val KEY_ESTIMATED_REMAINING_MILLIS = "estimated_remaining_millis"
private const val UNKNOWN_REMAINING_MILLIS = -1L

internal class AiModelDownloadStateStore(context: Context) {
  private val preferences = context.applicationContext.getSharedPreferences(
    DOWNLOAD_STATE_PREFERENCES,
    Context.MODE_PRIVATE,
  )

  val progress: Flow<AiModelDownloadProgress?> = callbackFlow {
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
      trySend(read())
    }
    trySend(read())
    preferences.registerOnSharedPreferenceChangeListener(listener)
    awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
  }.distinctUntilChanged()

  fun current(): AiModelDownloadProgress? = read()

  fun markQueued(modelId: String, totalBytes: Long) {
    update(
      AiModelDownloadProgress(
        modelId = modelId,
        phase = "queued",
        downloadedBytes = 0,
        totalBytes = totalBytes,
      ),
    )
  }

  fun markFailed(modelId: String, totalBytes: Long) {
    val current = read()?.takeIf { it.modelId == modelId }
    update(
      AiModelDownloadProgress(
        modelId = modelId,
        phase = "failed",
        downloadedBytes = current?.downloadedBytes ?: 0,
        totalBytes = current?.totalBytes?.takeIf { it > 0 } ?: totalBytes,
      ),
    )
  }

  fun update(progress: AiModelDownloadProgress) {
    preferences.edit()
      .putString(KEY_MODEL_ID, progress.modelId)
      .putString(KEY_PHASE, progress.phase)
      .putLong(KEY_DOWNLOADED_BYTES, progress.downloadedBytes)
      .putLong(KEY_TOTAL_BYTES, progress.totalBytes)
      .putLong(
        KEY_ESTIMATED_REMAINING_MILLIS,
        progress.estimatedRemainingMillis ?: UNKNOWN_REMAINING_MILLIS,
      )
      .apply()
  }

  private fun read(): AiModelDownloadProgress? {
    val modelId = preferences.getString(KEY_MODEL_ID, null)?.takeIf(String::isNotBlank) ?: return null
    val phase = preferences.getString(KEY_PHASE, null)?.takeIf(String::isNotBlank) ?: return null
    return AiModelDownloadProgress(
      modelId = modelId,
      phase = phase,
      downloadedBytes = preferences.getLong(KEY_DOWNLOADED_BYTES, 0),
      totalBytes = preferences.getLong(KEY_TOTAL_BYTES, 0),
      estimatedRemainingMillis = preferences
        .getLong(KEY_ESTIMATED_REMAINING_MILLIS, UNKNOWN_REMAINING_MILLIS)
        .takeIf { it >= 0 },
    )
  }
}

internal class AiModelDownloadScheduler(
  context: Context,
  private val stateStore: AiModelDownloadStateStore,
) {
  private val appContext = context.applicationContext

  fun schedule(modelId: String, estimatedBytes: Long) {
    val active = stateStore.current()?.takeIf { it.phase.isActiveDownloadPhase() }
    if (active?.modelId == modelId) return
    check(active == null) { "別のAIモデルをダウンロード中です" }

    stateStore.markQueued(modelId, estimatedBytes)
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        scheduleUserInitiatedJob(modelId, estimatedBytes)
      } else {
        scheduleForegroundWorker(modelId, estimatedBytes)
      }
    } catch (error: Throwable) {
      stateStore.markFailed(modelId, estimatedBytes)
      throw error
    }
  }

  @SuppressLint("NewApi")
  private fun scheduleUserInitiatedJob(modelId: String, estimatedBytes: Long) {
    val extras = PersistableBundle().apply {
      putString(INPUT_MODEL_ID, modelId)
      putLong(INPUT_MODEL_SIZE_BYTES, estimatedBytes)
    }
    val network = NetworkRequest.Builder()
      .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
      .build()
    val job = JobInfo.Builder(
      JOB_ID,
      ComponentName(appContext, AiModelDownloadJobService::class.java),
    )
      .setRequiredNetwork(network)
      .setUserInitiated(true)
      .setEstimatedNetworkBytes(estimatedBytes, 0L)
      .setExtras(extras)
      .build()
    val scheduler = appContext.getSystemService(JobScheduler::class.java)
    check(scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS) {
      "AIモデルのバックグラウンドダウンロードを開始できませんでした"
    }
  }

  private fun scheduleForegroundWorker(modelId: String, estimatedBytes: Long) {
    val request = OneTimeWorkRequestBuilder<AiModelDownloadWorker>()
      .setInputData(
        workDataOf(
          INPUT_MODEL_ID to modelId,
          INPUT_MODEL_SIZE_BYTES to estimatedBytes,
        ),
      )
      .setConstraints(
        Constraints.Builder()
          .setRequiredNetworkType(NetworkType.CONNECTED)
          .build(),
      )
      .build()
    WorkManager.getInstance(appContext).enqueueUniqueWork(
      WORK_NAME,
      ExistingWorkPolicy.REPLACE,
      request,
    )
  }
}

@SuppressLint("NewApi")
class AiModelDownloadJobService : JobService() {
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var runningDownload: RunningDownload? = null

  override fun onStartJob(params: JobParameters): Boolean {
    val modelId = params.extras.getString(INPUT_MODEL_ID)?.takeIf(String::isNotBlank) ?: return false
    val estimatedBytes = params.extras.getLong(INPUT_MODEL_SIZE_BYTES, 0)
    if (runningDownload != null) return false

    val manager = LocalModelManager(applicationContext)
    val stateStore = AiModelDownloadStateStore(applicationContext)
    val modelName = manager.models.value.firstOrNull { it.id == modelId }?.name ?: "AIモデル"
    AiModelDownloadNotifications.ensureChannel(applicationContext)
    setNotification(
      params,
      NOTIFICATION_ID,
      AiModelDownloadNotifications.progress(
        applicationContext,
        modelName,
        stateStore.current() ?: AiModelDownloadProgress(modelId, "queued", 0, estimatedBytes),
      ),
      JOB_END_NOTIFICATION_POLICY_REMOVE,
    )

    val stopRequested = AtomicBoolean(false)
    val job = serviceScope.launch {
      val progressJob = launch {
        manager.downloadProgress
          .filterNotNull()
          .collect { progress ->
            if (progress.modelId != modelId) return@collect
            val domainProgress = progress.toDomainProgress()
            stateStore.update(domainProgress)
            setNotification(
              params,
              NOTIFICATION_ID,
              AiModelDownloadNotifications.progress(applicationContext, modelName, domainProgress),
              JOB_END_NOTIFICATION_POLICY_REMOVE,
            )
          }
      }
      try {
        manager.downloadModel(modelId)
        manager.downloadProgress.value
          ?.takeIf { it.modelId == modelId }
          ?.let { stateStore.update(it.toDomainProgress()) }
        if (!stopRequested.get()) jobFinished(params, false)
      } catch (error: Throwable) {
        if (!stopRequested.get()) {
          if (error.isRetryableNetworkFailure()) {
            stateStore.markQueued(modelId, estimatedBytes)
            jobFinished(params, true)
          } else {
            stateStore.markFailed(modelId, estimatedBytes)
            jobFinished(params, false)
          }
        }
      } finally {
        progressJob.cancelAndJoin()
        manager.close()
        if (runningDownload?.jobId == params.jobId) runningDownload = null
      }
    }
    runningDownload = RunningDownload(params.jobId, modelId, estimatedBytes, stopRequested, job)
    return true
  }

  override fun onStopJob(params: JobParameters): Boolean {
    val running = runningDownload?.takeIf { it.jobId == params.jobId } ?: return true
    running.stopRequested.set(true)
    AiModelDownloadStateStore(applicationContext).markQueued(running.modelId, running.estimatedBytes)
    running.job.cancel()
    runningDownload = null
    return true
  }

  override fun onDestroy() {
    serviceScope.cancel()
    super.onDestroy()
  }

  private data class RunningDownload(
    val jobId: Int,
    val modelId: String,
    val estimatedBytes: Long,
    val stopRequested: AtomicBoolean,
    val job: Job,
  )
}

class AiModelDownloadWorker(
  appContext: Context,
  workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
  override suspend fun doWork(): Result {
    val modelId = inputData.getString(INPUT_MODEL_ID)?.takeIf(String::isNotBlank)
      ?: return Result.failure()
    val estimatedBytes = inputData.getLong(INPUT_MODEL_SIZE_BYTES, 0)
    val manager = LocalModelManager(applicationContext)
    val stateStore = AiModelDownloadStateStore(applicationContext)
    val modelName = manager.models.value.firstOrNull { it.id == modelId }?.name ?: "AIモデル"
    AiModelDownloadNotifications.ensureChannel(applicationContext)
    setForeground(
      AiModelDownloadNotifications.foregroundInfo(
        applicationContext,
        modelName,
        stateStore.current() ?: AiModelDownloadProgress(modelId, "queued", 0, estimatedBytes),
      ),
    )

    val progressJob = CoroutineScope(coroutineContext).launch {
      manager.downloadProgress
        .filterNotNull()
        .collect { progress ->
          if (progress.modelId != modelId) return@collect
          val domainProgress = progress.toDomainProgress()
          stateStore.update(domainProgress)
          setForeground(
            AiModelDownloadNotifications.foregroundInfo(
              applicationContext,
              modelName,
              domainProgress,
            ),
          )
        }
    }

    return try {
      manager.downloadModel(modelId)
      manager.downloadProgress.value
        ?.takeIf { it.modelId == modelId }
        ?.let { stateStore.update(it.toDomainProgress()) }
      Result.success()
    } catch (error: Throwable) {
      if (error.isRetryableNetworkFailure()) {
        stateStore.markQueued(modelId, estimatedBytes)
        Result.retry()
      } else {
        stateStore.markFailed(modelId, estimatedBytes)
        Result.failure()
      }
    } finally {
      progressJob.cancelAndJoin()
      manager.close()
    }
  }
}

private object AiModelDownloadNotifications {
  fun ensureChannel(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(
      NotificationChannel(
        NOTIFICATION_CHANNEL_ID,
        "AIモデルのダウンロード",
        NotificationManager.IMPORTANCE_LOW,
      ).apply {
        description = "ローカルAIモデルのダウンロード進捗"
        setShowBadge(false)
      },
    )
  }

  fun foregroundInfo(
    context: Context,
    modelName: String,
    progress: AiModelDownloadProgress,
  ): ForegroundInfo = ForegroundInfo(
    NOTIFICATION_ID,
    progress(context, modelName, progress),
    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
  )

  fun progress(
    context: Context,
    modelName: String,
    progress: AiModelDownloadProgress,
  ): Notification {
    val percent = if (progress.totalBytes > 0) {
      (progress.downloadedBytes * 100 / progress.totalBytes).coerceIn(0, 100).toInt()
    } else {
      0
    }
    return Notification.Builder(context, NOTIFICATION_CHANNEL_ID)
      .setSmallIcon(android.R.drawable.stat_sys_download)
      .setContentTitle("AIモデルをダウンロード中")
      .setContentText(downloadStatusText(modelName, progress, percent))
      .setCategory(Notification.CATEGORY_PROGRESS)
      .setOnlyAlertOnce(true)
      .setOngoing(progress.phase.isActiveDownloadPhase())
      .setProgress(100, percent, progress.totalBytes <= 0 || progress.phase == "queued")
      .apply {
        launchPendingIntent(context)?.let(::setContentIntent)
      }
      .build()
  }

  private fun launchPendingIntent(context: Context): PendingIntent? {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
    return PendingIntent.getActivity(
      context,
      0,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }

  private fun downloadStatusText(
    modelName: String,
    progress: AiModelDownloadProgress,
    percent: Int,
  ): String = when (progress.phase) {
    "queued" -> "$modelName の開始を待っています"
    "downloading" -> "$modelName $percent%"
    "verifying" -> "$modelName を確認しています"
    "completed" -> "$modelName のダウンロードが完了しました"
    "failed" -> "$modelName のダウンロードに失敗しました"
    else -> "$modelName ${progress.phase}"
  }
}

private fun ModelDownloadProgress.toDomainProgress() = AiModelDownloadProgress(
  modelId = modelId,
  phase = phase,
  downloadedBytes = downloadedBytes,
  totalBytes = totalBytes,
  estimatedRemainingMillis = estimatedRemainingMillis,
)

private fun String.isActiveDownloadPhase(): Boolean = this == "queued" || this == "downloading" || this == "verifying"

private fun Throwable.isRetryableNetworkFailure(): Boolean =
  generateSequence(this) { it.cause }.any { it is IOException }
