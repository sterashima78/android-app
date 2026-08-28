package dev.terashima.yomitorirss.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dev.terashima.yomitorirss.AppRouteDependencies
import dev.terashima.yomitorirss.feature.asset.ASSET_ROUTE
import dev.terashima.yomitorirss.feature.asset.AssetRoute
import dev.terashima.yomitorirss.feature.backup.BackupViewModel
import dev.terashima.yomitorirss.feature.calendar.CALENDAR_ROUTE
import dev.terashima.yomitorirss.feature.chat.CHAT_ROUTE
import dev.terashima.yomitorirss.feature.chat.ChatRoute
import dev.terashima.yomitorirss.feature.chat.ChatViewModel
import dev.terashima.yomitorirss.feature.game.GAME_ROUTE
import dev.terashima.yomitorirss.feature.health.HEALTH_ROUTE
import dev.terashima.yomitorirss.feature.health.HealthRoute
import dev.terashima.yomitorirss.feature.knowledge.KNOWLEDGE_ROUTE
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeRoute
import dev.terashima.yomitorirss.feature.library.LIBRARY_ROUTE
import dev.terashima.yomitorirss.feature.mail.MAIL_ROUTE
import dev.terashima.yomitorirss.feature.settings.AiSettingsViewModel
import dev.terashima.yomitorirss.feature.settings.SETTINGS_ROUTE
import dev.terashima.yomitorirss.feature.task.TASKS_ROUTE
import dev.terashima.yomitorirss.feature.task.TaskRoute
import dev.terashima.yomitorirss.feature.workout.WORKOUT_ROUTE
import dev.terashima.yomitorirss.feature.workout.WorkoutRoute
import dev.terashima.yomitorirss.feature.x.X_ROUTE
import dev.terashima.yomitorirss.feature.x.XViewerRoute
import dev.terashima.yomitorirss.feature.youtube.YOUTUBE_ROUTE

internal fun NavGraphBuilder.registerSingleFeatureDestinations(
  navController: NavHostController,
  routeDependencies: AppRouteDependencies,
  biometricLockEnabled: Boolean,
  onBiometricLockEnabledChange: (Boolean) -> Unit,
  onOpenWebContent: (String) -> Boolean,
  onOpenWebServer: () -> Unit,
  onGameFullscreenChange: (Boolean) -> Unit,
) {
  composable(LIBRARY_ROUTE) {
    LibraryRoute(
      dependencies = routeDependencies.library,
      onOpenWebContent = onOpenWebContent,
      modifier = Modifier.fillMaxSize(),
    )
  }
  composable(KNOWLEDGE_ROUTE) {
    KnowledgeRoute(
      viewModelFactory = routeDependencies.knowledgeViewModelFactory,
      modifier = Modifier.fillMaxSize(),
    )
  }
  composable(ASSET_ROUTE) {
    AssetRoute(
      viewModelFactory = routeDependencies.assetViewModelFactory,
      modifier = Modifier.fillMaxSize(),
    )
  }
  composable(MAIL_ROUTE) {
    MailRouteHost(
      modifier = Modifier.fillMaxSize(),
      routeDependencies = routeDependencies,
    )
  }
  composable(YOUTUBE_ROUTE) {
    YouTubeRouteHost(
      viewModelFactory = routeDependencies.youtubeViewModelFactory,
      modifier = Modifier.fillMaxSize(),
    )
  }
  composable(X_ROUTE) {
    XViewerRoute(
      repository = routeDependencies.xViewerCssRepository,
      modifier = Modifier.fillMaxSize(),
    )
  }
  composable(TASKS_ROUTE) {
    TaskRoute(
      viewModelFactory = routeDependencies.taskViewModelFactory,
      modifier = Modifier.fillMaxSize(),
    )
  }
  composable(CALENDAR_ROUTE) {
    CalendarRoute(
      viewModelFactory = routeDependencies.calendarViewModelFactory,
      modifier = Modifier.fillMaxSize(),
    )
  }
  composable(GAME_ROUTE) {
    GameRouteHost(
      modifier = Modifier.fillMaxSize(),
      onFullscreenChange = onGameFullscreenChange,
    )
  }
  composable(HEALTH_ROUTE) {
    HealthRoute(
      viewModelFactory = routeDependencies.health.viewModelFactory,
      readPermissions = routeDependencies.health.readPermissions,
      modifier = Modifier.fillMaxSize(),
    )
  }
  composable(WORKOUT_ROUTE) {
    WorkoutRoute(
      viewModelFactory = routeDependencies.workout.viewModelFactory,
      aiViewModelFactory = routeDependencies.workout.aiViewModelFactory,
      writePermissions = routeDependencies.workout.writePermissions,
      modifier = Modifier.fillMaxSize(),
    )
  }
  composable(CHAT_ROUTE) {
    val chatViewModel: ChatViewModel = viewModel(factory = routeDependencies.chatViewModelFactory)
    ChatRoute(modifier = Modifier.fillMaxSize(), chatViewModel = chatViewModel)
  }
  composable(SETTINGS_ROUTE) {
    val backupViewModel: BackupViewModel = viewModel(factory = routeDependencies.backupViewModelFactory)
    val aiSettingsViewModel: AiSettingsViewModel = viewModel(factory = routeDependencies.aiSettingsViewModelFactory)
    SettingsRoute(
      modifier = Modifier.fillMaxSize(),
      backupViewModel = backupViewModel,
      aiSettingsViewModel = aiSettingsViewModel,
      aiTaskQueueRepository = routeDependencies.aiTaskQueueRepository,
      initialBackgroundFetchWifiOnly = routeDependencies.backgroundFetchWifiOnly(),
      onBackgroundFetchWifiOnlyChange = routeDependencies::setBackgroundFetchWifiOnly,
      biometricLockEnabled = biometricLockEnabled,
      onBiometricLockEnabledChange = onBiometricLockEnabledChange,
      onOpenWebServer = onOpenWebServer,
      onNavigate = { route -> navController.navigateTopLevel(route) },
    )
  }
}
