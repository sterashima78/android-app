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
import dev.terashima.yomitorirss.core.background.LocalAiBackgroundTaskGate
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
    val execution = LocalAiBackgroundExecutionPreferences(appContext)
    if (execution.paused) {
      setResumeOnChargingScheduled(true)
      return
    }

    setResumeOnChargingScheduled(false)
    enqueueBatchWork()
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

  internal fun kickFromChargingResume() {
    // Do not cancel RESUME_ON_CHARGING_WORK_NAME here: this method is called by that worker.
    // The normal worker checks the shared execution gate again before doing any AI work, so a
    // concurrent user pause remains authoritative even if this enqueue races with it.
    enqueueBatchWork()
  }

  private fun enqueueBatchWork() {
    val request = OneTimeWorkRequestBuilder<LibraryOrganizationBatchWorker>()
      .addTag(LibraryOrganizationBatchWorker.WORK_TAG)
      .build()
    WorkManager.getInstance(appContext).enqueueUniqueWork(
      LibraryOrganizationBatchWorker.WORK_NAME,
      ExistingWorkPolicy.APPEND_OR_REPLACE,
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

    // This worker is only armed for a globally paused, still-running library batch. An explicitly
    // paused batch keeps its PAUSED state and is never resumed only because charging started.
    execution.paused = false

    val database = YomitoriDatabase.create(applicationContext)
    try {
      val repository = DefaultLibraryOrganizationRepository(DatabaseConnection(database))
      when (repository.batchSnapshot()?.status) {
        LibraryOrganizationBatchStatus.RUNNING -> {
          DataChangeNotifier.shared.notifyChanged()
          WorkManagerLibraryOrganizationBatchScheduler(applicationContext).kickFromChargingResume()
        }
        LibraryOrganizationBatchStatus.PAUSED,
        LibraryOrganizationBatchStatus.COMPLETED,
        null -> Unit
      }
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
  override suspend fun doWork(): Result {
    val execution = LocalAiBackgroundExecutionPreferences(applicationContext)
    if (execution.paused) {
      WorkManagerLibraryOrganizationBatchScheduler(applicationContext).kick()
      return Result.success()
    }

    setForeground(createForegroundInfo("AIタスクの実行を待っています"))
    return LocalAiBackgroundTaskGate.withPermit {
      withContext(Dispatchers.IO) {
        if (LocalAiBackgroundExecutionPreferences(applicationContext).paused) {
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
