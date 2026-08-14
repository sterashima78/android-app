package dev.terashima.yomitorirss.feature.library

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.terashima.yomitorirss.core.background.isBackgroundDataFetchAllowed
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import dev.terashima.yomitorirss.feature.library.data.AudibleCoverEnrichmentRepository
import dev.terashima.yomitorirss.feature.library.data.GoogleBooksAuthorizationManager
import dev.terashima.yomitorirss.feature.library.data.KindleCoverEnrichmentRepository
import dev.terashima.yomitorirss.feature.library.data.LibraryCoverStatusRepository
import dev.terashima.yomitorirss.feature.library.data.continueAudibleCoverEnrichment
import dev.terashima.yomitorirss.feature.library.data.continueKindleCoverEnrichment
import java.io.IOException
import kotlinx.coroutines.CancellationException

class KindleCoverEnrichmentWorker(
  appContext: Context,
  workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
  override suspend fun doWork(): Result {
    if (!isBackgroundDataFetchAllowed(applicationContext)) {
      continueKindleCoverEnrichment(applicationContext)
      return Result.success()
    }

    val database = YomitoriDatabase.create(applicationContext)
    val connection = DatabaseConnection(database)
    val authorizationManager = GoogleBooksAuthorizationManager(applicationContext)
    val repository = KindleCoverEnrichmentRepository(
      database = connection,
      googleBooksAccessTokenProvider = authorizationManager::existingAccessTokenOrNull,
    )
    return try {
      if (repository.enrichNext()) {
        continueKindleCoverEnrichment(applicationContext)
      } else {
        repository.nextWakeDelayMillis()?.let { delayMillis ->
          continueKindleCoverEnrichment(applicationContext, delayMillis)
        }
      }
      Result.success()
    } catch (error: CancellationException) {
      throw error
    } catch (_: Throwable) {
      Result.failure()
    } finally {
      database.close()
    }
  }
}

class AudibleCoverEnrichmentWorker(
  appContext: Context,
  workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
  override suspend fun doWork(): Result {
    if (!isBackgroundDataFetchAllowed(applicationContext)) {
      continueAudibleCoverEnrichment(applicationContext)
      return Result.success()
    }

    val database = YomitoriDatabase.create(applicationContext)
    val connection = DatabaseConnection(database)
    val repository = AudibleCoverEnrichmentRepository(connection)
    val coverStatusRepository = LibraryCoverStatusRepository(connection)
    return try {
      if (repository.enrichBatch()) {
        continueAudibleCoverEnrichment(applicationContext)
      }
      Result.success()
    } catch (error: IOException) {
      if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
        Result.retry()
      } else {
        if (coverStatusRepository.markNextAudibleCoverLookupError(error.message)) {
          continueAudibleCoverEnrichment(applicationContext)
        }
        Result.success()
      }
    } catch (error: CancellationException) {
      throw error
    } catch (_: Throwable) {
      Result.failure()
    } finally {
      database.close()
    }
  }

  private companion object {
    const val MAX_RETRY_ATTEMPTS = 2
  }
}
