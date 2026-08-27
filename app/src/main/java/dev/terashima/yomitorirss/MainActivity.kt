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
import dev.terashima.yomitorirss.diagnostics.CrashDiagnosticsContent
import dev.terashima.yomitorirss.diagnostics.StartupCrashStore
import dev.terashima.yomitorirss.diagnostics.copyCrashReport
import dev.terashima.yomitorirss.entry.IncomingIntentHandler
import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.platform.LanWebServerDialogHost
import dev.terashima.yomitorirss.platform.openWebContentInCustomTab
import dev.terashima.yomitorirss.security.AppLockContent
import dev.terashima.yomitorirss.security.AppLockCoordinator
import dev.terashima.yomitorirss.security.AppLockSessionViewModel
import dev.terashima.yomitorirss.ui.AppViewModel
import dev.terashima.yomitorirss.ui.YomitoriApp
import dev.terashima.yomitorirss.ui.YomitoriTheme

class MainActivity : ComponentActivity() {
  private val appViewModel: AppViewModel by viewModels()
  private val appLockSession: AppLockSessionViewModel by viewModels()
  private val dependencies: MainActivityDependencies by lazy(LazyThreadSafetyMode.NONE) {
    val provider = application as? MainActivityDependenciesProvider
      ?: error("Application must implement MainActivityDependenciesProvider")
    provider.mainActivityDependencies
  }
  private val incomingIntentHandler by lazy(LazyThreadSafetyMode.NONE) {
    IncomingIntentHandler(
      activity = this,
      appViewModel = appViewModel,
      dependencies = dependencies,
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
          else -> MainContent()
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
  private fun MainContent() {
    var showWebServer by remember { mutableStateOf(false) }

    YomitoriApp(
      appViewModel = appViewModel,
      routeDependencies = dependencies.routeDependencies,
      biometricLockEnabled = appLockCoordinator.enabled,
      onBiometricLockEnabledChange = appLockCoordinator::setEnabled,
      onOpenArticle = ::openArticle,
      onOpenWebServer = { showWebServer = true },
      onExitApp = ::finish,
    )

    LanWebServerDialogHost(
      visible = showWebServer,
      controller = dependencies.lanWebServerController,
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
