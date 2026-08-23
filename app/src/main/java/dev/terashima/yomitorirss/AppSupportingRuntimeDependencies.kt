package dev.terashima.yomitorirss

import android.app.Application
import dev.terashima.yomitorirss.core.database.DataChangeNotifier
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
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
import dev.terashima.yomitorirss.feature.task.TaskRepository
import dev.terashima.yomitorirss.feature.task.data.DefaultTaskRepository
import dev.terashima.yomitorirss.feature.web.LanWebServerController
import dev.terashima.yomitorirss.feature.web.data.AndroidLanWebServerController
import dev.terashima.yomitorirss.feature.workout.WorkoutRepository
import dev.terashima.yomitorirss.feature.workout.data.DefaultWorkoutRepository
import dev.terashima.yomitorirss.feature.x.XViewerCssRepository
import dev.terashima.yomitorirss.feature.x.data.SharedPreferencesXViewerCssRepository

/** Independent/supporting feature repositories and Android platform adapters. */
internal class AppSupportingRuntimeDependencies(
  private val application: Application,
  private val database: YomitoriDatabase,
  private val databaseConnection: DatabaseConnection,
  private val dataChanges: DataChangeNotifier,
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
    GmailAuthorizationManager(application)
  }

  val mailRepository: MailRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultMailRepository(
      context = application,
      database = databaseConnection,
      authorization = gmailAuthorizationManager,
    )
  }

  val backupRepository: BackupRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultBackupRepository(application, database, dataChanges)
  }

  val xViewerCssRepository: XViewerCssRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    SharedPreferencesXViewerCssRepository(application)
  }
}
