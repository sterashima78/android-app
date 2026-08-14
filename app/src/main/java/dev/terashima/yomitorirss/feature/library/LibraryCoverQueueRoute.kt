package dev.terashima.yomitorirss.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.work.WorkInfo
import dev.terashima.yomitorirss.YomitoriApplication
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.library.data.LibraryCoverStatusRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LibraryCoverQueueRoute(onDismiss: () -> Unit) {
  val application = LocalContext.current.applicationContext as YomitoriApplication
  val repository = remember(application) {
    LibraryCoverStatusRepository(DatabaseConnection(application.container.database))
  }
  val kindleScheduler = remember(application) { KindleCoverEnrichmentScheduler(application) }
  val audibleScheduler = remember(application) { AudibleCoverEnrichmentScheduler(application) }
  val kindleWorkInfos by kindleScheduler.workInfos.collectAsState(initial = emptyList())
  val audibleWorkInfos by audibleScheduler.workInfos.collectAsState(initial = emptyList())
  var snapshot by remember { mutableStateOf(LibraryCoverAcquisitionSnapshot()) }
  var refreshVersion by remember { mutableIntStateOf(0) }
  var message by remember { mutableStateOf<String?>(null) }
  val scope = rememberCoroutineScope()

  LaunchedEffect(kindleWorkInfos, audibleWorkInfos, refreshVersion) {
    snapshot = withContext(Dispatchers.IO) { repository.snapshot() }
  }

  val workStates = mapOf(
    LibrarySource.KINDLE to coverWorkState(
      kindleWorkInfos,
      enabled = snapshot.kindleCoverEnrichmentEnabled,
    ),
    LibrarySource.AUDIBLE to coverWorkState(audibleWorkInfos),
  )

  LibraryCoverQueueScreen(
    snapshot = snapshot,
    workStates = workStates,
    message = message,
    onRetryUnresolved = {
      scope.launch {
        try {
          val sources = buildSet {
            add(LibrarySource.AUDIBLE)
            if (snapshot.kindleCoverEnrichmentEnabled) add(LibrarySource.KINDLE)
          }
          withContext(Dispatchers.IO) { repository.resetUnresolvedLookups(sources) }
          if (snapshot.kindleCoverEnrichmentEnabled) kindleScheduler.schedule(force = true)
          audibleScheduler.schedule()
          message = "未取得の表紙を再試行します"
          refreshVersion++
        } catch (error: CancellationException) {
          throw error
        } catch (error: Throwable) {
          message = error.message ?: "表紙の再試行に失敗しました"
        }
      }
    },
    onCancelCurrentWork = {
      kindleScheduler.cancel()
      audibleScheduler.cancel()
      message = "現在の表紙取得をキャンセルしました"
    },
    onDismiss = onDismiss,
  )
}

private fun coverWorkState(
  workInfos: List<WorkInfo>,
  enabled: Boolean = true,
): LibraryCoverWorkState {
  if (!enabled) return LibraryCoverWorkState.DISABLED
  val active = workInfos.filterNot { it.state.isFinished }
  if (active.any { it.state == WorkInfo.State.RUNNING }) return LibraryCoverWorkState.RUNNING
  if (active.any { it.state == WorkInfo.State.ENQUEUED && it.runAttemptCount > 0 }) {
    return LibraryCoverWorkState.RETRY_WAITING
  }
  if (active.isNotEmpty()) return LibraryCoverWorkState.WAITING
  return if (workInfos.lastOrNull()?.state == WorkInfo.State.FAILED) {
    LibraryCoverWorkState.FAILED
  } else {
    LibraryCoverWorkState.IDLE
  }
}
