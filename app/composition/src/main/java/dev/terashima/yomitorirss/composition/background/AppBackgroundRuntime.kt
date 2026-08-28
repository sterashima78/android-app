package dev.terashima.yomitorirss.composition.background

import android.app.Application
import dev.terashima.yomitorirss.core.database.DataChangeNotifier
import dev.terashima.yomitorirss.core.database.PersistenceChangeNotifier
import dev.terashima.yomitorirss.feature.backup.data.AndroidBackupChangeScheduler
import dev.terashima.yomitorirss.feature.backup.data.BackupPreferenceChangeObserver
import dev.terashima.yomitorirss.feature.backup.data.PersistenceBackupChangeObserver
import dev.terashima.yomitorirss.feature.summary.data.BookmarkAutoEnrichmentBackfillScheduler
import dev.terashima.yomitorirss.feature.widget.UnreadArticlesWidgetRefreshObserver
import kotlinx.coroutines.flow.filter

/** Application-scope background observers and one-shot startup scheduling. */
internal class AppBackgroundRuntime(
  private val application: Application,
) {
  private val persistenceBackupChangeObserver: PersistenceBackupChangeObserver by lazy(
    LazyThreadSafetyMode.SYNCHRONIZED,
  ) {
    PersistenceBackupChangeObserver(
      dataChanges = PersistenceChangeNotifier.shared.version.filter { it > 0L },
      scheduler = AndroidBackupChangeScheduler(application),
    )
  }

  private val backupPreferenceChangeObserver: BackupPreferenceChangeObserver by lazy(
    LazyThreadSafetyMode.SYNCHRONIZED,
  ) {
    BackupPreferenceChangeObserver(application, PersistenceChangeNotifier.shared)
  }

  private val unreadArticlesWidgetRefreshObserver: UnreadArticlesWidgetRefreshObserver by lazy(
    LazyThreadSafetyMode.SYNCHRONIZED,
  ) {
    UnreadArticlesWidgetRefreshObserver(application, DataChangeNotifier.shared.version)
  }

  fun start() {
    persistenceBackupChangeObserver.start()
    backupPreferenceChangeObserver.start()
    unreadArticlesWidgetRefreshObserver.start()
    runCatching { BookmarkAutoEnrichmentBackfillScheduler.schedule(application) }
  }
}
