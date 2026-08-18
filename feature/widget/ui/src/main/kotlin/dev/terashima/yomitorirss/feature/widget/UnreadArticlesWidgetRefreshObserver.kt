package dev.terashima.yomitorirss.feature.widget

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class UnreadArticlesWidgetRefreshObserver(
  context: Context,
  private val dataChanges: Flow<Long>,
) {
  private val applicationContext = context.applicationContext
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var job: Job? = null

  fun start() {
    if (job != null) return
    job = dataChanges
      .onEach {
        runCatching { UnreadArticlesWidgetUpdater.updateAll(applicationContext) }
      }
      .launchIn(scope)
  }
}
