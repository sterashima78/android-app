package dev.terashima.yomitorirss

import android.app.Application
import dev.terashima.yomitorirss.core.background.BackgroundDataFetchPreferences
import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueRepository
import dev.terashima.yomitorirss.feature.asset.AssetViewModel
import dev.terashima.yomitorirss.feature.backup.BackupViewModel
import dev.terashima.yomitorirss.feature.calendar.CalendarViewModel
import dev.terashima.yomitorirss.feature.health.HealthViewModel
import dev.terashima.yomitorirss.feature.settings.AiSettingsViewModel
import dev.terashima.yomitorirss.feature.task.TaskChangeNotifyingRepository
import dev.terashima.yomitorirss.feature.task.TaskViewModel
import dev.terashima.yomitorirss.feature.widget.TaskWidgetUpdater
import dev.terashima.yomitorirss.feature.workout.WorkoutViewModel
import dev.terashima.yomitorirss.feature.x.XViewerCssRepository

internal class AppSupportingRouteDependencies(
  private val application: Application,
  private val container: AppContainer,
) {
  private val backgroundDataFetchPreferences by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    BackgroundDataFetchPreferences(application)
  }

  val backupViewModelFactory: BackupViewModel.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    BackupViewModel.Factory(container.backupRepository)
  }

  val aiSettingsViewModelFactory: AiSettingsViewModel.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    AiSettingsViewModel.Factory(
      repository = container.aiModelRepository,
      summaryPromptSettings = container.summaryPromptSettings,
    )
  }

  val aiTaskQueueRepository: AiTaskQueueRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    container.aiTaskQueueRepository
  }

  val assetViewModelFactory: AssetViewModel.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    AssetViewModel.Factory(
      repository = container.assetRepository,
      onChanged = container.backupChangeScheduler::scheduleAfterChange,
    )
  }

  val health: HealthRouteDependencies by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    val repository = container.healthRepository
    HealthRouteDependencies(
      viewModelFactory = HealthViewModel.Factory(repository),
      readPermissions = repository.requestPermissions(),
    )
  }

  val taskViewModelFactory: TaskViewModel.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    val repository = TaskChangeNotifyingRepository(container.taskRepository) {
      runCatching { TaskWidgetUpdater.updateAll(application) }
    }
    TaskViewModel.Factory(repository)
  }

  val calendarViewModelFactory: CalendarViewModel.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    CalendarViewModel.Factory(container.calendarRepository)
  }

  val workoutViewModelFactory: WorkoutViewModel.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    WorkoutViewModel.Factory(
      repository = container.workoutRepository,
      historyExporter = WorkoutHealthConnectExporter(container.healthRepository),
    )
  }

  val xViewerCssRepository: XViewerCssRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    container.xViewerCssRepository
  }

  fun backgroundFetchWifiOnly(): Boolean = backgroundDataFetchPreferences.wifiOnly

  fun setBackgroundFetchWifiOnly(wifiOnly: Boolean) {
    backgroundDataFetchPreferences.wifiOnly = wifiOnly
    container.mailRepository.refreshPeriodicSyncPolicy()
  }
}

data class HealthRouteDependencies internal constructor(
  val viewModelFactory: HealthViewModel.Factory,
  val readPermissions: Set<String>,
)
