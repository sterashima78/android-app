package dev.terashima.yomitorirss.feature.backup.data

import dev.terashima.yomitorirss.feature.backup.BackupChangeScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** Schedules a debounced backup whenever durable application data changes. */
class DatabaseBackupChangeObserver(
  private val dataChanges: Flow<Long>,
  private val scheduler: BackupChangeScheduler,
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
  private var job: Job? = null

  fun start() {
    if (job != null) return
    job = dataChanges
      .drop(1)
      .onEach { scheduler.scheduleAfterChange() }
      .launchIn(scope)
  }
}
