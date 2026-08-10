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
  private val workManager = WorkManager.getInstance(context.applicationContext)

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
      .setConstraints(networkConstraints())
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
      .setConstraints(networkConstraints())
      .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
      .build()
    workManager.enqueueUniquePeriodicWork(
      PERIODIC_WORK_NAME,
      ExistingPeriodicWorkPolicy.UPDATE,
      request,
    )
  }

  fun cancelAccount(accountId: String) {
    workManager.cancelAllWorkByTag(accountWorkTag(accountId))
  }

  fun cancelPeriodic() {
    workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
  }

  private fun networkConstraints() = Constraints.Builder()
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
    val database = YomitoriDatabase.create(applicationContext)
    val repository = DefaultMailRepository(
      context = applicationContext,
      database = DatabaseConnection(database),
      authorization = GmailAuthorizationManager(applicationContext),
    )
    val initialSync = inputData.getBoolean(INPUT_INITIAL_SYNC, false)
    val accountId = inputData.getString(INPUT_ACCOUNT_ID)
    return try {
      if (initialSync) {
        require(!accountId.isNullOrBlank()) { "初回同期の Gmail アカウントが指定されていません" }
        val expectedPageToken = if (inputData.getBoolean(INPUT_HAS_PAGE_TOKEN, false)) {
          inputData.getString(INPUT_PAGE_TOKEN)
        } else {
          null
        }
        when (val step = repository.syncInitialPage(accountId, expectedPageToken)) {
          is InitialSyncStep.Continue -> {
            MailSyncScheduler(applicationContext).scheduleInitialPage(accountId, step.nextPageToken)
            Result.success()
          }
          InitialSyncStep.Complete -> Result.success()
          InitialSyncStep.Stale -> {
            // The previous attempt may have persisted its next checkpoint and been interrupted
            // before enqueueing the continuation. Reconcile from the durable DB state here.
            repository.sync(accountId)
            Result.success()
          }
        }
      } else {
        repository.sync(accountId)
        Result.success()
      }
    } catch (error: MailAuthorizationRequiredException) {
      if (initialSync && accountId != null) {
        repository.markInitialSyncError(accountId, error.message)
        Result.failure()
      } else {
        Result.success()
      }
    } catch (error: IOException) {
      if (initialSync && accountId != null) {
        repository.markInitialSyncWaitingForNetwork(accountId, error.message)
      }
      Result.retry()
    } catch (error: GmailApiException) {
      val retryable = error.statusCode == 429 || error.statusCode >= 500
      if (initialSync && accountId != null) {
        if (retryable) {
          repository.markInitialSyncWaitingForNetwork(accountId, error.message)
        } else {
          repository.markInitialSyncError(accountId, error.message)
        }
      }
      if (retryable) Result.retry() else Result.failure()
    } catch (error: Throwable) {
      if (initialSync && accountId != null) {
        repository.markInitialSyncError(accountId, error.message ?: "Gmail の同期に失敗しました")
      }
      Result.failure()
    } finally {
      database.close()
    }
  }
}
