package dev.terashima.yomitorirss.feature.library.data

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
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.terashima.yomitorirss.core.airuntime.LocalModelManager
import dev.terashima.yomitorirss.core.background.LocalAiBackgroundExecutionPreferences
import dev.terashima.yomitorirss.core.database.DataChangeNotifier
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationBatchScheduler
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationBatchStatus
import dev.terashima.yomitorirss.feature.library.organizationKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class WorkManagerLibraryOrganizationBatchScheduler(
  context: Context,
) : LibraryOrganizationBatchScheduler {
  private val appContext = context.applicationContext

  override fun kick() {
    val workManager = WorkManager.getInstance(appContext)
    val execution = LocalAiBackgroundExecutionPreferences(appContext)
    if (execution.paused) {
      setResumeOnChargingScheduled(true)
      return
    }

    setResumeOnChargingScheduled(false)
    val request = OneTimeWorkRequestBuilder<LibraryOrganizationBatchWorker>()
      .addTag(LibraryOrganizationBatchWorker.WORK_TAG)
      .build()
    workManager.enqueueUniqueWork(
      LibraryOrganizationBatchWorker.WORK_NAME,
      ExistingWorkPolicy.APPEND_OR_REPLACE,
      request,
    )
  }

  override fun cancel() {
    WorkManager.getInstance(appContext).cancelUniqueWork(LibraryOrganizationBatchWorker.WORK_NAME)
  }

  override fun setResumeOnChargingScheduled(enabled: Boolean) {
    val workManager = WorkManager.getInstance(appContext)
    if (!enabled) {
      workManager.cancelUniqueWork(RESUME_ON_CHARGING_WORK_NAME)
      return
    }

    val execution = LocalAiBackgroundExecutionPreferences(appContext)
    if (!execution.paused || !execution.resumeWhenCharging) return

    val request = OneTimeWorkRequestBuilder<LibraryOrganizationResumeOnChargingWorker>()
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

  private companion object {
    const val RESUME_ON_CHARGING_WORK_NAME = "library-ai-organization-resume-on-charging"
  }
}

class LibraryOrganizationResumeOnChargingWorker(
  appContext: Context,
  params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
  override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
    val execution = LocalAiBackgroundExecutionPreferences(applicationContext)
    if (!execution.resumeWhenCharging) return@withContext Result.success()

    // This preference is shared with the summary queue. Either charging worker may clear the
    // global gate first, so both workers treat the operation as idempotent.
    execution.paused = false

    val database = YomitoriDatabase.create(applicationContext)
    try {
      val repository = DefaultLibraryOrganizationRepository(DatabaseConnection(database))
      when (repository.batchSnapshot()?.status) {
        LibraryOrganizationBatchStatus.PAUSED -> repository.resumeBatch()
        LibraryOrganizationBatchStatus.RUNNING -> Unit
        LibraryOrganizationBatchStatus.COMPLETED,
        null -> return@withContext Result.success()
      }
      DataChangeNotifier.shared.notifyChanged()
      WorkManagerLibraryOrganizationBatchScheduler(applicationContext).kick()
      Result.success()
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (_: Throwable) {
      Result.retry()
    } finally {
      database.close()
    }
  }
}

class LibraryOrganizationBatchWorker(
  appContext: Context,
  params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
  override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
    val execution = LocalAiBackgroundExecutionPreferences(applicationContext)
    if (execution.paused) {
      WorkManagerLibraryOrganizationBatchScheduler(applicationContext).kick()
      return@withContext Result.success()
    }

    val database = YomitoriDatabase.create(applicationContext)
    val connection = DatabaseConnection(database)
    val organizationRepository = DefaultLibraryOrganizationRepository(connection)
    val libraryRepository = SeriesAwareLibraryRepository(connection)
    val modelManager = LocalModelManager(applicationContext)
    val suggester = LocalLibraryOrganizationSuggester(modelManager)
    var currentItem: ClaimedLibraryOrganizationBatchItem? = null

    try {
      organizationRepository.requeueInterruptedBatchItems()
      DataChangeNotifier.shared.notifyChanged()

      while (true) {
        currentCoroutineContext().ensureActive()
        if (LocalAiBackgroundExecutionPreferences(applicationContext).paused) break

        val item = organizationRepository.claimNextBatchItem()
        if (item == null) {
          val batch = organizationRepository.batchSnapshot()
          if (batch?.status == LibraryOrganizationBatchStatus.RUNNING) {
            organizationRepository.finishBatchIfIdle(batch.batchId)
            DataChangeNotifier.shared.notifyChanged()
          }
          break
        }
        currentItem = item

        try {
          val library = libraryRepository.snapshot()
          val book = (library.books + library.hiddenBooks)
            .firstOrNull { candidate -> candidate.organizationKey() == item.key }
          if (book == null) {
            organizationRepository.skipBatchItem(item, "蔵書が見つからないためスキップしました")
            DataChangeNotifier.shared.notifyChanged()
            currentItem = null
            continue
          }

          val currentOrganization = organizationRepository.snapshot().organizationFor(book)
          if (currentOrganization.tags.isNotEmpty() || currentOrganization.collections.isNotEmpty()) {
            organizationRepository.skipBatchItem(item, "別の操作ですでに整理済みです")
            DataChangeNotifier.shared.notifyChanged()
            currentItem = null
            continue
          }

          setForeground(createForegroundInfo(book.title))
          val (existingTags, existingCollections) =
            organizationRepository.batchTaxonomyContext(item.batchId)
          currentCoroutineContext().ensureActive()
          val suggestion = suggester.suggest(
            book = book,
            existingTags = existingTags,
            existingCollections = existingCollections,
          )
          currentCoroutineContext().ensureActive()
          organizationRepository.saveGeneratedCandidate(item, suggestion)
          DataChangeNotifier.shared.notifyChanged()
          currentItem = null
        } catch (cancelled: CancellationException) {
          throw cancelled
        } catch (error: Throwable) {
          organizationRepository.failBatchItem(item, error.userMessage())
          DataChangeNotifier.shared.notifyChanged()
          currentItem = null
        }
      }

      Result.success()
    } catch (cancelled: CancellationException) {
      currentItem?.let(organizationRepository::requeueBatchItem)
      DataChangeNotifier.shared.notifyChanged()
      throw cancelled
    } finally {
      modelManager.close()
      database.close()
    }
  }

  private fun createForegroundInfo(bookTitle: String): ForegroundInfo {
    val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
    notificationManager.createNotificationChannel(
      NotificationChannel(
        CHANNEL_ID,
        "蔵書のAI整理",
        NotificationManager.IMPORTANCE_LOW,
      ).apply {
        description = "ローカルAIで蔵書の整理候補をバックグラウンド生成している間に表示します"
        setShowBadge(false)
      },
    )

    val notificationBuilder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.stat_notify_sync)
      .setContentTitle("蔵書をAIで整理しています")
      .setContentText(bookTitle)
      .setStyle(NotificationCompat.BigTextStyle().bigText(bookTitle))
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

  companion object {
    internal const val WORK_NAME = "library-ai-organization-queue"
    internal const val WORK_TAG = "library-ai-organization"
    private const val CHANNEL_ID = "library_ai_organization"
    private const val NOTIFICATION_ID = 8768
  }
}

private fun Throwable.userMessage(): String =
  generateSequence(this) { it.cause }
    .mapNotNull(Throwable::message)
    .firstOrNull(String::isNotBlank)
    ?: javaClass.simpleName
