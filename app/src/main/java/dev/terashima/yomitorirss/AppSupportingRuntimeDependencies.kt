package dev.terashima.yomitorirss

import android.app.Application
import dev.terashima.yomitorirss.core.database.DataChangeNotifier
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.database.PersistenceChangeNotifier
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.feature.asset.AssetRepository
import dev.terashima.yomitorirss.feature.asset.data.DefaultAssetRepository
import dev.terashima.yomitorirss.feature.backup.BackupRepository
import dev.terashima.yomitorirss.feature.backup.data.DefaultBackupRepository
import dev.terashima.yomitorirss.feature.calendar.CalendarRepository
import dev.terashima.yomitorirss.feature.calendar.data.DefaultCalendarRepository
import dev.terashima.yomitorirss.feature.chat.ChatRepository
import dev.terashima.yomitorirss.feature.chat.data.DefaultChatRepository
import dev.terashima.yomitorirss.feature.mail.MailRepository
import dev.terashima.yomitorirss.feature.mail.data.DefaultMailRepository
import dev.terashima.yomitorirss.feature.mail.data.GmailAuthorizationManager
import dev.terashima.yomitorirss.feature.mail.data.GmailAuthorizationOutcome
import dev.terashima.yomitorirss.feature.task.TaskRepository
import dev.terashima.yomitorirss.feature.task.data.DefaultTaskRepository
import dev.terashima.yomitorirss.feature.web.LanWebServerController
import dev.terashima.yomitorirss.feature.web.data.AndroidLanWebServerController
import dev.terashima.yomitorirss.feature.widget.WidgetRefreshScheduler
import dev.terashima.yomitorirss.feature.widget.data.WorkManagerWidgetRefreshScheduler
import dev.terashima.yomitorirss.feature.workout.WorkoutRepository
import dev.terashima.yomitorirss.feature.workout.data.DefaultWorkoutRepository
import dev.terashima.yomitorirss.feature.workout.data.HealthConnectWorkoutHistoryExporter
import dev.terashima.yomitorirss.feature.x.XViewerCssRepository
import dev.terashima.yomitorirss.feature.x.data.SharedPreferencesXViewerCssRepository

/** Independent/supporting feature repositories and Android platform adapters. */
internal class AppSupportingRuntimeDependencies(
  private val application: Application,
  private val database: YomitoriDatabase,
  private val databaseConnection: DatabaseConnection,
  private val dataChanges: DataChangeNotifier,
  private val persistenceChanges: PersistenceChangeNotifier,
  private val httpClient: HttpClient,
) {
  val assetRepository: AssetRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultAssetRepository(application, databaseConnection)
  }

  val chatRepository: ChatRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultChatRepository(databaseConnection)
  }

  val taskRepository: TaskRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultTaskRepository(databaseConnection)
  }

  val workoutRepository: WorkoutRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultWorkoutRepository(application)
  }

  val workoutHistoryExporter: HealthConnectWorkoutHistoryExporter by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    HealthConnectWorkoutHistoryExporter(application)
  }

  val calendarRepository: CalendarRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultCalendarRepository(
      context = application,
      taskReader = taskRepository,
      workoutReader = workoutRepository,
    )
  }

  val lanWebServerController: LanWebServerController by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    AndroidLanWebServerController(application)
  }

  val gmailAuthorizationManager: GmailAuthorizationManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    GmailAuthorizationManager(application, httpClient)
  }

  val mailAuthorization: MailAuthorizationDependencies by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    MailAuthorizationDependencies(
      requestAccount = {
        when (val outcome = gmailAuthorizationManager.requestAccount()) {
          is GmailAuthorizationOutcome.Authorized -> MailAuthorizationOutcome.Authorized(
            MailAuthorizedAccount(
              email = outcome.account.email,
              displayName = outcome.account.displayName,
              accessToken = outcome.account.accessToken,
            ),
          )
          is GmailAuthorizationOutcome.RequiresResolution ->
            MailAuthorizationOutcome.RequiresResolution(outcome.pendingIntent)
        }
      },
      resultFromIntent = { data ->
        gmailAuthorizationManager.resultFromIntent(data).let { account ->
          MailAuthorizedAccount(
            email = account.email,
            displayName = account.displayName,
            accessToken = account.accessToken,
          )
        }
      },
    )
  }

  val mailRepository: MailRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultMailRepository(
      context = application,
      database = databaseConnection,
      authorization = gmailAuthorizationManager,
    )
  }

  val widgetRefreshScheduler: WidgetRefreshScheduler by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    WorkManagerWidgetRefreshScheduler(application)
  }

  val backupRepository: BackupRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultBackupRepository(application, database, dataChanges, persistenceChanges)
  }

  val xViewerCssRepository: XViewerCssRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    SharedPreferencesXViewerCssRepository(application)
  }
}
