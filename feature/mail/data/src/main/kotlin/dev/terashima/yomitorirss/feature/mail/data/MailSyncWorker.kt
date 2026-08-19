package dev.terashima.yomitorirss.feature.mail.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.terashima.yomitorirss.core.background.backgroundDataFetchConstraints
import dev.terashima.yomitorirss.core.background.isBackgroundDataFetchAllowed
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import dev.terashima.yomitorirss.feature.mail.MailAuthorizationRequiredException
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.TimeUnit

private const val INPUT_ACCOUNT_ID = "mail_account_id"
private const val INPUT_INITIAL_SYNC = "mail_initial_sync"
private const val INPUT_HAS_PAGE_TOKEN = "mail_has_page_token"
private const val INPUT_PAGE_TOKEN = "mail_page_token"
private const val PERIODIC_WORK_NAME = "gmail-mail-sync"
private const val ACCOUNT_WORK_TAG_PREFIX = "gmail-mail-account:"

class MailSyncScheduler(context: Context) {
  private val appContext = context.applicationContext
  private val workManager: WorkManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    WorkManager.getInstance(appContext)
  }

  fun scheduleInitialPage(accountId: String, expectedPageToken: String?) {
    if (expectedPageToken == null) schedulePeriodic()
    val request = OneTimeWorkRequestBuilder<MailSyncWorker>()
      .setInputData(
        workDataOf(
          INPUT_ACCOUNT_ID to accountId,
          INPUT_INITIAL_SYNC to true,
          INPUT_HAS_PAGE_TOKEN to (expectedPageToken != null),
          INPUT_PAGE_TOKEN to expectedPageToken.orEmpty(),
        ),
      )
      .setConstraints(connectedConstraints())
      .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
      .addTag(accountWorkTag(accountId))
      .build()
    workManager.enqueueUniqueWork(
      initialPageWorkName(accountId, expectedPageToken),
      ExistingWorkPolicy.KEEP,
      request,
    )
  }

  fun schedulePeriodic() {
    val request = PeriodicWorkRequestBuilder<MailSyncWorker>(30, TimeUnit.MINUTES)
      .setConstraints(backgroundDataFetchConstraints(appContext))
      .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
      .build()
    workManager.enqueueUniquePeriodicWork(
      PERIODIC_WORK_NAME,
      ExistingPeriodicWorkPolicy.UPDATE,
      request,
    )
  }

  fun refreshPeriodicNetworkPolicy() {
    val workInfos = workManager.getWorkInfosForUniqueWork(PERIODIC_WORK_NAME)
    workInfos.addListener(
      {
        if (runCatching { workInfos.get().isNotEmpty() }.getOrDefault(false)) {
          schedulePeriodic()
        }
      },
      appContext.mainExecutor,
    )
  }

  fun cancelAccount(accountId: String) {
    workManager.cancelAllWorkByTag(accountWorkTag(accountId))
  }

  fun cancelPeriodic() {
    workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
  }

  private fun connectedConstraints() = Constraints.Builder()
    .setRequiredNetworkType(NetworkType.CONNECTED)
    .build()

  private fun initialPageWorkName(accountId: String, pageToken: String?): String {
    val marker = pageToken ?: "initial"
    val tokenId = UUID.nameUUIDFromBytes(marker.toByteArray(StandardCharsets.UTF_8))
    return "gmail-mail-initial:$accountId:$tokenId"
  }

  private fun accountWorkTag(accountId: String) = "$ACCOUNT_WORK_TAG_PREFIX$accountId"
}

class MailSyncWorker(
  appContext: Context,
  workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
  override suspend fun doWork(): Result {
    val initialSync = inputData.getBoolean(INPUT_INITIAL_SYNC, false)
    if (!initialSync && !isBackgroundDataFetchAllowed(applicationContext)) return Result.success()

    val database = YomitoriDatabase.create(applicationContext)
    val repository = DefaultMailRepository(
      context = applicationContext,
      database = DatabaseConnection(database),
      authorization = GmailAuthorizationManager(applicationContext),
    )
    val accountId = inputData.getString(INPUT_ACCOUNT_ID)
    return try {
      if (initialSync) {
        require(!accountId.isNullOrBlank()) { "初回同期の Gmail アカウントが指定されていません" }
        val expectedPageToken = inputData.getString(INPUT_PAGE_TOKEN)
          ?.takeIf { inputData.getBoolean(INPUT_HAS_PAGE_TOKEN, false) }
        when (val step = repository.syncInitialPage(accountId, expectedPageToken)) {
          is InitialSyncStep.Continue -> {
            MailSyncScheduler(applicationContext).scheduleInitialPage(accountId, step.nextPageToken)
          }
          InitialSyncStep.Complete,
          InitialSyncStep.Stale -> Unit
        }
      } else {
        repository.sync(accountId)
      }
      Result.success()
    } catch (error: MailAuthorizationRequiredException) {
      if (initialSync && !accountId.isNullOrBlank()) {
        repository.markInitialSyncError(accountId, error.message)
      }
      Result.failure(workDataOf("error" to (error.message ?: "Google アカウントの再認証が必要です")))
    } catch (error: IOException) {
      if (initialSync && !accountId.isNullOrBlank()) {
        repository.markInitialSyncWaitingForNetwork(accountId, error.message)
      }
      Result.retry()
    } catch (error: GmailApiException) {
      if (initialSync && !accountId.isNullOrBlank()) {
        repository.markInitialSyncError(accountId, error.message)
      }
      if (error.statusCode in 500..599) Result.retry()
      else Result.failure(workDataOf("error" to (error.message ?: "Gmail の同期に失敗しました")))
    } catch (error: Exception) {
      if (initialSync && !accountId.isNullOrBlank()) {
        repository.markInitialSyncError(accountId, error.message)
      }
      Result.failure(workDataOf("error" to (error.message ?: "Gmail の同期に失敗しました")))
    }
  }
}
