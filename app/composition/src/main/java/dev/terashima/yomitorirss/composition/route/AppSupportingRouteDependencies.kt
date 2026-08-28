package dev.terashima.yomitorirss.composition.route

import android.app.Application
import dev.terashima.yomitorirss.AppContainer
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
import dev.terashima.yomitorirss.feature.workout.WorkoutAiViewModel
import dev.terashima.yomitorirss.feature.workout.WorkoutViewModel
import dev.terashima.yomitorirss.feature.workout.data.DefaultWorkoutAiAdvisor
import dev.terashima.yomitorirss.feature.workout.data.DefaultWorkoutAiSettingsRepository
import dev.terashima.yomitorirss.feature.workout.data.HealthConnectWorkoutHistoryExporter
import dev.terashima.yomitorirss.feature.x.XViewerCssRepository

internal class AppSupportingRouteDependencies(
  private val application: Application,
  private val container: AppContainer,
) {
  private val backgroundDataFetchPreferences by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    BackgroundDataFetchPreferences(application)
  }

  private val workoutAiSettingsRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultWorkoutAiSettingsRepository(application)
  }

  val backupViewModelFactory: BackupViewModel.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    BackupViewModel.Factory(container.backupRepository)
  }

  val aiSettingsViewModelFactory: AiSettingsViewModel.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    AiSettingsViewModel.Factory(
      repository = container.aiModelRepository,
      summaryPromptSettings = container.summaryPromptSettings,
      chatGptDebugRepository = container.chatGptDebugRepository,
      chatGptProviderRepository = container.chatGptProviderRepository,
      summaryExecutionSettings = container.summaryExecutionSettings,
      knowledgeExecutionSettings = container.knowledgeExecutionSettings,
    )
  }

  val aiTaskQueueRepository: AiTaskQueueRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    container.aiTaskQueueRepository
  }

  val assetViewModelFactory: AssetViewModel.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    AssetViewModel.Factory(container.assetRepository)
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

  val workout: WorkoutRouteDependencies by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    WorkoutRouteDependencies(
      viewModelFactory = WorkoutViewModel.Factory(
        repository = container.workoutRepository,
        historyExporter = container.workoutHistoryExporter,
      ),
      aiViewModelFactory = WorkoutAiViewModel.Factory(
        workoutReader = container.workoutRepository,
        settingsRepository = workoutAiSettingsRepository,
        advisor = DefaultWorkoutAiAdvisor(
          localInference = container.textInference,
          cloudInference = container.cloudTextInference,
        ),
      ),
      writePermissions = HealthConnectWorkoutHistoryExporter.WRITE_PERMISSIONS,
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

data class WorkoutRouteDependencies internal constructor(
  val viewModelFactory: WorkoutViewModel.Factory,
  val aiViewModelFactory: WorkoutAiViewModel.Factory,
  val writePermissions: Set<String>,
)
