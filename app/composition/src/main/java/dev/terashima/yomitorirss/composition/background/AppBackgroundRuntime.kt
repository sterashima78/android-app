package dev.terashima.yomitorirss.composition.background

import android.app.Application
import dev.terashima.yomitorirss.core.database.DataChangeNotifier
import dev.terashima.yomitorirss.feature.backup.data.GoogleDriveBackupScheduler
import dev.terashima.yomitorirss.feature.mail.data.MailSyncScheduler
import dev.terashima.yomitorirss.feature.summary.data.BookmarkAutoEnrichmentBackfillScheduler
import dev.terashima.yomitorirss.feature.widget.UnreadArticlesWidgetRefreshObserver

/** Application-scope background observers and one-shot startup scheduling. */
internal class AppBackgroundRuntime(
  private val application: Application,
) {
  private val unreadArticlesWidgetRefreshObserver: UnreadArticlesWidgetRefreshObserver by lazy(
    LazyThreadSafetyMode.SYNCHRONIZED,
  ) {
    UnreadArticlesWidgetRefreshObserver(application, DataChangeNotifier.shared.version)
  }

  fun start() {
    runCatching { GoogleDriveBackupScheduler.ensureScheduled(application) }
    unreadArticlesWidgetRefreshObserver.start()
    runCatching { BookmarkAutoEnrichmentBackfillScheduler.schedule(application) }
    runCatching { MailSyncScheduler(application).cancelPeriodic() }
    runCatching { IntegratedRefreshScheduler.schedule(application) }
  }
}
