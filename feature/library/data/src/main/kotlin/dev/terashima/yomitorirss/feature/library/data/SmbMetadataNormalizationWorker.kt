package dev.terashima.yomitorirss.feature.library.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import dev.terashima.yomitorirss.core.airuntime.LocalModelManager
import dev.terashima.yomitorirss.core.background.LocalAiBackgroundExecutionPreferences
import dev.terashima.yomitorirss.core.background.LocalAiBackgroundTaskGate
import dev.terashima.yomitorirss.core.background.LocalAiBackgroundTaskPriority
import dev.terashima.yomitorirss.core.database.DataChangeNotifier
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import dev.terashima.yomitorirss.feature.library.LibrarySource
import dev.terashima.yomitorirss.feature.library.SmbMetadataNormalizationBatchStatus
import dev.terashima.yomitorirss.feature.library.SmbMetadataNormalizationScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

class WorkManagerSmbMetadataNormalizationScheduler(
  context: Context,
) : SmbMetadataNormalizationScheduler {
  private val appContext = context.applicationContext

  override fun kick() {
    val execution = LocalAiBackgroundExecutionPreferences(appContext)
    if (execution.paused) {
      setResumeOnChargingScheduled(true)
      return
    }
    setResumeOnChargingScheduled(false)
    enqueueWork()
  }

  override suspend fun cancel() {
    WorkManager.getInstance(appContext)
      .cancelUniqueWork(SmbMetadataNormalizationWorker.WORK_NAME)
      .await()
  }

  override fun setResumeOnChargingScheduled(enabled: Boolean) {
    val workManager = WorkManager.getInstance(appContext)
    if (!enabled) {
      workManager.cancelUniqueWork(RESUME_ON_CHARGING_WORK_NAME)
      return
    }
    val execution = LocalAiBackgroundExecutionPreferences(appContext)
    if (!execution.paused || !execution.resumeWhenCharging) return
    val request = OneTimeWorkRequestBuilder<SmbMetadataNormalizationResumeOnChargingWorker>()
      .setConstraints(Constraints.Builder().setRequiresCharging(true).build())
      .build()
    workManager.enqueueUniqueWork(
      RESUME_ON_CHARGING_WORK_NAME,
      ExistingWorkPolicy.KEEP,
      request,
    )
  }

  internal fun kickFromChargingResume() = enqueueWork()

  private fun enqueueWork() {
    val request = OneTimeWorkRequestBuilder<SmbMetadataNormalizationWorker>()
      .setBackoffCriteria(BackoffPolicy.LINEAR, MIN_RETRY_SECONDS, TimeUnit.SECONDS)
      .addTag(SmbMetadataNormalizationWorker.WORK_TAG)
      .build()
    WorkManager.getInstance(appContext).enqueueUniqueWork(
      SmbMetadataNormalizationWorker.WORK_NAME,
      ExistingWorkPolicy.APPEND_OR_REPLACE,
      request,
    )
  }

  private companion object {
    const val RESUME_ON_CHARGING_WORK_NAME = "smb-metadata-normalization-resume-on-charging"
    const val MIN_RETRY_SECONDS = 30L
  }
}

class SmbMetadataNormalizationResumeOnChargingWorker(
  appContext: Context,
  params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
  override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
    val execution = LocalAiBackgroundExecutionPreferences(applicationContext)
    if (!execution.resumeWhenCharging) return@withContext Result.success()
    execution.paused = false
    WorkManagerSmbMetadataNormalizationScheduler(applicationContext).kickFromChargingResume()
    Result.success()
  }
}

class SmbMetadataNormalizationWorker(
  appContext: Context,
  params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
  override suspend fun doWork(): Result {
    val execution = LocalAiBackgroundExecutionPreferences(applicationContext)
    if (execution.paused) {
      WorkManagerSmbMetadataNormalizationScheduler(applicationContext).setResumeOnChargingScheduled(true)
      return Result.success()
    }
    setForeground(createForegroundInfo("AIタスクの実行を待っています"))
    return withContext(Dispatchers.IO) {
      val database = YomitoriDatabase.create(applicationContext)
      val connection = DatabaseConnection(database)
      val smbRepository = CleaningSmbLibraryRepository(applicationContext, connection)
      val repository = DefaultSmbMetadataNormalizationRepository(connection, smbRepository)
      val promptTemplate = SharedPreferencesSmbMetadataNormalizationPromptRepository(applicationContext).prompt()
      var current: ClaimedSmbMetadataNormalizationItem? = null
      try {
        repository.requeueInterrupted()
        repository.promoteCoverReadyItems()
        DataChangeNotifier.shared.notifyChanged()

        while (!LocalAiBackgroundExecutionPreferences(applicationContext).paused) {
          currentCoroutineContext().ensureActive()
          var claimed = false
          LocalAiBackgroundTaskGate.withPermit(LocalAiBackgroundTaskPriority.LOW) {
            if (LocalAiBackgroundExecutionPreferences(applicationContext).paused) return@withPermit
            val item = repository.claimNext() ?: return@withPermit
            claimed = true
            current = item
            DataChangeNotifier.shared.notifyChanged()

            try {
              val book = currentSmbBook(connection, item.sourceId)
                ?: run {
                  repository.skip(item, "対象のファイルサーバ書籍が見つかりません")
                  current = null
                  return@withPermit
                }
              val input = smbNormalizationInput(book)
                ?: run {
                  repository.skip(item, "対象書籍のファイル情報を読み取れません")
                  current = null
                  return@withPermit
                }
              if (
                input.fileName != item.originalFileName ||
                input.size != item.inputSize ||
                input.modifiedAt != item.inputModifiedAt
              ) {
                repository.skip(item, "一括解析開始後にファイルが変更されました")
                current = null
                return@withPermit
              }
              val coverFile = localCoverFile(book.thumbnailUrl)
              if (coverFile == null) {
                repository.fail(item, "表紙キャッシュが失われたため再取得します")
                connection.writable.update(
                  "library_items",
                  ContentValues().apply { putNull("thumbnail_url") },
                  "source = ? AND source_id = ?",
                  arrayOf(LibrarySource.SMB.name, item.sourceId),
                )
                repository.retryCandidate(item.sourceId)
                if (smbRepository.enqueueMissingCoverPrefetch() > 0) {
                  WorkManagerSmbCoverPrefetchScheduler(applicationContext).enqueue()
                }
                current = null
                return@withPermit
              }
              require(coverFile.length() <= MAX_COVER_BYTES) { "表紙画像が大きすぎます" }

              setForeground(createForegroundInfo(item.originalFileName))
              currentCoroutineContext().ensureActive()
              val suggester = LocalSmbMetadataNormalizationSuggester(
                LocalModelManager.shared(applicationContext),
              )
              val proposal = suggester.suggest(
                currentFileName = item.originalFileName,
                coverBytes = coverFile.readBytes(),
                promptTemplate = promptTemplate,
              )
              currentCoroutineContext().ensureActive()
              repository.saveGeneratedCandidate(
                item = item,
                proposedFileName = normalizedSmbBookFileName(item.originalFileName, proposal),
                proposal = proposal,
              )
              current = null
            } catch (cancelled: CancellationException) {
              throw cancelled
            } catch (error: Throwable) {
              repository.fail(item, error.userMessage())
              current = null
            }
            DataChangeNotifier.shared.notifyChanged()
          }
          if (!claimed) break
        }

        repository.promoteCoverReadyItems()
        val batch = repository.batchSnapshot()
        if (batch?.status == SmbMetadataNormalizationBatchStatus.RUNNING) {
          repository.finishBatchIfIdle(batch.batchId)
        }
        DataChangeNotifier.shared.notifyChanged()
        val latest = repository.batchSnapshot()
        if ((latest?.waitingForCover ?: 0) > 0) Result.retry() else Result.success()
      } catch (cancelled: CancellationException) {
        current?.let(repository::requeue)
        DataChangeNotifier.shared.notifyChanged()
        throw cancelled
      } catch (_: Throwable) {
        Result.retry()
      } finally {
        database.close()
      }
    }
  }

  private fun createForegroundInfo(label: String): ForegroundInfo {
    val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
    notificationManager.createNotificationChannel(
      NotificationChannel(
        CHANNEL_ID,
        "ファイルサーバ書誌正規化",
        NotificationManager.IMPORTANCE_LOW,
      ).apply {
        description = "ローカルAIで表紙とファイル名から書誌情報の候補を生成している間に表示します"
        setShowBadge(false)
      },
    )
    val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.stat_notify_sync)
      .setContentTitle("書誌情報を解析しています")
      .setContentText(label)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setCategory(NotificationCompat.CATEGORY_PROGRESS)
      .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
    applicationContext.packageManager
      .getLaunchIntentForPackage(applicationContext.packageName)
      ?.let { intent ->
        PendingIntent.getActivity(
          applicationContext,
          0,
          intent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
      }
      ?.let(builder::setContentIntent)
    val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
    } else {
      0
    }
    return ForegroundInfo(NOTIFICATION_ID, builder.build(), serviceType)
  }

  companion object {
    internal const val WORK_NAME = "smb-metadata-normalization"
    internal const val WORK_TAG = "smb-metadata-normalization"
    private const val CHANNEL_ID = "smb_metadata_normalization"
    private const val NOTIFICATION_ID = 8770
    private const val MAX_COVER_BYTES = 8L * 1024 * 1024
  }
}

private suspend fun currentSmbBook(
  database: DatabaseConnection,
  sourceId: String,
) = DefaultLibraryRepository(database).snapshot().let { snapshot ->
  (snapshot.books + snapshot.hiddenBooks).firstOrNull {
    it.source == LibrarySource.SMB && it.sourceId == sourceId
  }
}

private fun localCoverFile(url: String?): File? {
  val uri = url?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return null
  if (uri.scheme != "file") return null
  return uri.path?.let(::File)?.takeIf { it.isFile && it.length() > 0L }
}

private fun Throwable.userMessage(): String =
  generateSequence(this) { it.cause }
    .mapNotNull(Throwable::message)
    .firstOrNull(String::isNotBlank)
    ?: javaClass.simpleName