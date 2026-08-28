package dev.terashima.yomitorirss

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import dev.terashima.yomitorirss.diagnostics.CrashDiagnosticsContent
import dev.terashima.yomitorirss.diagnostics.StartupCrashStore
import dev.terashima.yomitorirss.diagnostics.copyCrashReport
import dev.terashima.yomitorirss.entry.IncomingIntentHandler
import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.platform.openWebContentInCustomTab
import dev.terashima.yomitorirss.security.AppLockContent
import dev.terashima.yomitorirss.security.AppLockCoordinator
import dev.terashima.yomitorirss.security.AppLockSessionViewModel
import dev.terashima.yomitorirss.ui.AppNavigationTarget
import dev.terashima.yomitorirss.ui.LanWebServerDialogHost
import dev.terashima.yomitorirss.ui.YomitoriApp
import dev.terashima.yomitorirss.ui.YomitoriTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

class MainActivity : ComponentActivity() {
  private val appLockSession: AppLockSessionViewModel by viewModels()
  private val navigationRequests = Channel<AppNavigationTarget>(Channel.BUFFERED)
  private val navigationRequestFlow = navigationRequests.receiveAsFlow()
  private val dependencyProvider: MainActivityDependenciesProvider by lazy(LazyThreadSafetyMode.NONE) {
    application as? MainActivityDependenciesProvider
      ?: error("Application must implement MainActivityDependenciesProvider")
  }
  private val presentationDependencies by lazy(LazyThreadSafetyMode.NONE) {
    dependencyProvider.mainActivityPresentationDependencies
  }
  private val lanWebDependencies by lazy(LazyThreadSafetyMode.NONE) {
    dependencyProvider.mainActivityLanWebDependencies
  }
  private val incomingIntentDependencies by lazy(LazyThreadSafetyMode.NONE) {
    dependencyProvider.incomingIntentDependencies
  }
  private val incomingIntentHandler by lazy(LazyThreadSafetyMode.NONE) {
    IncomingIntentHandler(
      activity = this,
      onNavigate = { target -> navigationRequests.trySend(target) },
      dependencies = incomingIntentDependencies,
    )
  }
  private val appLockCoordinator by lazy(LazyThreadSafetyMode.NONE) {
    AppLockCoordinator(
      activity = this,
      session = appLockSession,
      onUnlocked = { consumeIncomingIntent(intent) },
    )
  }

  private var showingCrashDiagnostics = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    appLockCoordinator.initialize()

    val crashReport = StartupCrashStore.peek(this)
    showingCrashDiagnostics = crashReport != null

    setContent {
      YomitoriTheme {
        // Keep the navigation owner above the app-lock branch. The lock screen can replace
        // MainContent temporarily without discarding the current back stack or destination VMs.
        val navController = rememberNavController()
        when {
          appLockCoordinator.enabled && !appLockCoordinator.unlocked ->
            AppLockContent(onUnlock = appLockCoordinator::requestUnlock)
          crashReport != null ->
            CrashDiagnosticsContent(
              report = crashReport,
              onCopy = { copyCrashReport(crashReport) },
              onRetry = {
                StartupCrashStore.clear(this@MainActivity)
                recreate()
              },
            )
          else -> MainContent(navController)
        }
      }
    }

    if (crashReport == null && appLockCoordinator.unlocked) {
      consumeIncomingIntent(intent)
    }
  }

  override fun onStart() {
    super.onStart()
    appLockCoordinator.onStart()
  }

  override fun onResume() {
    super.onResume()
    appLockCoordinator.onResume()
  }

  override fun onPause() {
    appLockCoordinator.onPause()
    super.onPause()
  }

  override fun onStop() {
    appLockCoordinator.onStop()
    super.onStop()
  }

  @Composable
  private fun MainContent(navController: NavHostController) {
    var showWebServer by remember { mutableStateOf(false) }

    YomitoriApp(
      navController = navController,
      routeDependencies = presentationDependencies.routeDependencies,
      navigationRequests = navigationRequestFlow,
      biometricLockEnabled = appLockCoordinator.enabled,
      onBiometricLockEnabledChange = appLockCoordinator::updateEnabled,
      onOpenArticle = ::openArticle,
      onOpenWebContent = { url -> openWebContentInCustomTab(url) },
      onOpenWebServer = { showWebServer = true },
      onExitApp = ::finish,
    )

    LanWebServerDialogHost(
      visible = showWebServer,
      controller = lanWebDependencies.controller,
      onDismiss = { showWebServer = false },
    )
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    if (
      showingCrashDiagnostics ||
      (appLockCoordinator.enabled && !appLockCoordinator.unlocked)
    ) {
      return
    }
    consumeIncomingIntent(intent)
  }

  private fun consumeIncomingIntent(incoming: Intent) {
    if (showingCrashDiagnostics) return
    incomingIntentHandler.consume(incoming)
  }

  private fun openArticle(article: Article) {
    if (!openWebContentInCustomTab(article.url)) {
      Toast.makeText(this, "記事を開けませんでした", Toast.LENGTH_LONG).show()
    }
  }
}
