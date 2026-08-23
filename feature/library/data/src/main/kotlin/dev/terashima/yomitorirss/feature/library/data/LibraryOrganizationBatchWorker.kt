package dev.terashima.yomitorirss.feature.library.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import dev.terashima.yomitorirss.core.background.LocalAiBackgroundExecutionPreferences
import dev.terashima.yomitorirss.core.background.LocalAiBackgroundTaskGate
import dev.terashima.yomitorirss.core.background.LocalAiBackgroundTaskPriority
import dev.terashima.yomitorirss.core.database.DataChangeNotifier
import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationBatchScheduler
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationBatchStatus
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationDraft
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationSeriesContext
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationSnapshot
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationSuggestion
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationSuggester
import dev.terashima.yomitorirss.feature.library.LibraryRepository
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

  override suspend fun cancel() {
    WorkManager.getInstance(appContext)
      .cancelUniqueWork(LibraryOrganizationBatchWorker.WORK_NAME)
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

    val request = OneTimeWorkRequestBuilder<LibraryOrganizationResumeOnChargingWorker>()
      .setConstraints(
        Constraints.Builder()
          .setRequiresCharging(true)
          .build(),
      )
      .build()
    WorkManager.getInstance(appContext).enqueueUniqueWork(
      RESUME_ON_CHARGING_WORK_NAME,
      ExistingWorkPolicy.KEEP,
      request,
    )
  }

  internal fun kickFromChargingResume() {
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
  private val repository: DefaultLibraryOrganizationRepository,
  private val scheduler: WorkManagerLibraryOrganizationBatchScheduler,
) : CoroutineWorker(appContext, params) {
  override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
    val execution = LocalAiBackgroundExecutionPreferences(applicationContext)
    if (!execution.resumeWhenCharging) return@withContext Result.success()

    execution.paused = false

    try {
      when (repository.batchSnapshot()?.status) {
        LibraryOrganizationBatchStatus.RUNNING -> {
          DataChangeNotifier.shared.notifyChanged()
          scheduler.kickFromChargingResume()
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
    }
  }
}

class LibraryOrganizationBatchWorker(
  appContext: Context,
  params: WorkerParameters,
  private val organizationRepository: DefaultLibraryOrganizationRepository,
  private val libraryRepository: LibraryRepository,
  private val suggester: LibraryOrganizationSuggester,
  private val scheduler: WorkManagerLibraryOrganizationBatchScheduler,
) : CoroutineWorker(appContext, params) {
  override suspend fun doWork(): Result {
    val execution = LocalAiBackgroundExecutionPreferences(applicationContext)
    if (execution.paused) {
      scheduler.kick()
      return Result.success()
    }

    setForeground(createForegroundInfo("AIタスクの実行を待っています"))
    return withContext(Dispatchers.IO) {
      var currentItem: ClaimedLibraryOrganizationBatchItem? = null

      try {
        organizationRepository.requeueInterruptedBatchItems()
        DataChangeNotifier.shared.notifyChanged()

        while (!LocalAiBackgroundExecutionPreferences(applicationContext).paused) {
          currentCoroutineContext().ensureActive()
          var claimedItem = false
          LocalAiBackgroundTaskGate.withPermit(LocalAiBackgroundTaskPriority.NORMAL) {
            if (LocalAiBackgroundExecutionPreferences(applicationContext).paused) return@withPermit

            val item = organizationRepository.claimNextBatchItem()
            if (item == null) {
              val batch = organizationRepository.batchSnapshot()
              if (batch?.status == LibraryOrganizationBatchStatus.RUNNING) {
                organizationRepository.finishBatchIfIdle(batch.batchId)
                DataChangeNotifier.shared.notifyChanged()
              }
              return@withPermit
            }
            claimedItem = true
            currentItem = item

            try {
              val library = libraryRepository.snapshot()
              val allBooks = library.books + library.hiddenBooks
              val book = allBooks.firstOrNull { candidate -> candidate.organizationKey() == item.key }
              if (book == null) {
                organizationRepository.skipBatchItem(item, "蔵書が見つからないためスキップしました")
                DataChangeNotifier.shared.notifyChanged()
                currentItem = null
                return@withPermit
              }

              val organizationSnapshot = organizationRepository.snapshot()
              val currentOrganization = organizationSnapshot.organizationFor(book)
              if (currentOrganization.tags.isNotEmpty() || currentOrganization.collections.isNotEmpty()) {
                organizationRepository.skipBatchItem(item, "別の操作ですでに整理済みです")
                DataChangeNotifier.shared.notifyChanged()
                currentItem = null
                return@withPermit
              }

              setForeground(createForegroundInfo(book.title))
              val (existingTags, existingCollections) =
                organizationRepository.batchTaxonomyContext(item.batchId)
              val seriesContext = seriesOrganizationContextFor(
                book = book,
                books = allBooks,
                organizationSnapshot = organizationSnapshot,
              )
              currentCoroutineContext().ensureActive()
              val suggestion = suggester.suggest(
                book = book,
                existingTags = existingTags,
                existingCollections = existingCollections,
                seriesContext = seriesContext,
              )
              currentCoroutineContext().ensureActive()
              persistAndAutoApplySuggestion(
                repository = organizationRepository,
                item = item,
                book = book,
                suggestion = suggestion,
              )
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

          if (!claimedItem) break
        }
        Result.success()
      } catch (cancelled: CancellationException) {
        currentItem?.let(organizationRepository::requeueBatchItem)
        DataChangeNotifier.shared.notifyChanged()
        throw cancelled
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
        description = "ローカルAIで蔵書のタグ・コレクションをバックグラウンド整理している間に表示します"
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

    return ForegroundInfo(
      NOTIFICATION_ID,
      notificationBuilder.build(),
      ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
    )
  }

  companion object {
    internal const val WORK_NAME = "library-ai-organization-queue"
    internal const val WORK_TAG = "library-ai-organization"
    private const val CHANNEL_ID = "library_ai_organization"
    private const val NOTIFICATION_ID = 8768
  }
}

private suspend fun persistAndAutoApplySuggestion(
  repository: DefaultLibraryOrganizationRepository,
  item: ClaimedLibraryOrganizationBatchItem,
  book: LibraryBook,
  suggestion: LibraryOrganizationSuggestion,
) {
  repository.saveGeneratedCandidate(item, suggestion)
  try {
    repository.acceptCandidate(
      book = book,
      draft = LibraryOrganizationDraft(
        tagNames = suggestion.tagNames,
        collectionNames = suggestion.collectionNames,
        readingStatus = null,
      ),
    )
  } catch (cancelled: CancellationException) {
    throw cancelled
  } catch (error: Throwable) {
    val currentOrganization = try {
      repository.snapshot().organizationFor(book)
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (_: Throwable) {
      null
    }
    val manuallyOrganized = currentOrganization?.let { organization ->
      organization.tags.isNotEmpty() || organization.collections.isNotEmpty()
    } == true
    if (!manuallyOrganized) throw error

    try {
      repository.rejectCandidate(item.key)
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (_: Throwable) {
      // The manual organization already won. A stale candidate can remain as recovery state.
    }
  }
}

internal fun seriesOrganizationContextFor(
  book: LibraryBook,
  books: List<LibraryBook>,
  organizationSnapshot: LibraryOrganizationSnapshot,
): LibraryOrganizationSeriesContext? {
  val targetSeries = book.series ?: return null
  val peerOrganizations = books.asSequence()
    .filter { peer -> peer.organizationKey() != book.organizationKey() }
    .filter { peer -> sameSeries(targetSeries.id, targetSeries.name, peer.series?.id, peer.series?.name) }
    .map(organizationSnapshot::organizationFor)
    .toList()

  val tagNames = peerOrganizations
    .flatMap { organization -> organization.tags.map { tag -> tag.name } }
    .distinctBy(::normalizeLibraryOrganizationName)
    .take(MAX_SERIES_CONTEXT_TAGS)
  val collectionNames = peerOrganizations
    .flatMap { organization -> organization.collections.map { collection -> collection.name } }
    .distinctBy(::normalizeLibraryOrganizationName)
    .take(MAX_SERIES_CONTEXT_COLLECTIONS)

  if (tagNames.isEmpty() && collectionNames.isEmpty()) return null
  return LibraryOrganizationSeriesContext(
    tagNames = tagNames,
    collectionNames = collectionNames,
  )
}

private fun sameSeries(
  leftId: String?,
  leftName: String,
  rightId: String?,
  rightName: String?,
): Boolean {
  val normalizedLeftId = leftId?.trim()?.takeIf(String::isNotEmpty)
  val normalizedRightId = rightId?.trim()?.takeIf(String::isNotEmpty)
  if (normalizedLeftId != null && normalizedRightId != null) {
    return normalizedLeftId.equals(normalizedRightId, ignoreCase = true)
  }
  val normalizedLeftName = leftName.trim()
  val normalizedRightName = rightName?.trim().orEmpty()
  if (normalizedLeftName.isEmpty() || normalizedRightName.isEmpty()) return false
  return normalizedRightName.equals(normalizedLeftName, ignoreCase = true)
}

private fun Throwable.userMessage(): String =
  generateSequence(this) { it.cause }
    .mapNotNull(Throwable::message)
    .firstOrNull(String::isNotBlank)
    ?: javaClass.simpleName

private const val MAX_SERIES_CONTEXT_TAGS = 20
private const val MAX_SERIES_CONTEXT_COLLECTIONS = 10
