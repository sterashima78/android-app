package dev.terashima.yomitorirss.feature.library

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.terashima.yomitorirss.core.background.isBackgroundDataFetchAllowed
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import dev.terashima.yomitorirss.feature.library.data.GoogleBooksAuthorizationManager
import dev.terashima.yomitorirss.feature.library.data.KindleCoverEnrichmentRepository
import dev.terashima.yomitorirss.feature.library.data.continueKindleCoverEnrichment
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
