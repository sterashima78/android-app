package dev.terashima.yomitorirss

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Bundle
import android.os.CancellationSignal
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.bookmark.BookmarkSaveResult
import dev.terashima.yomitorirss.feature.web.WebServerDialog
import dev.terashima.yomitorirss.feature.widget.TaskWidgetProvider
import dev.terashima.yomitorirss.feature.widget.UnreadArticlesWidgetProvider
import dev.terashima.yomitorirss.ui.AppViewModel
import dev.terashima.yomitorirss.ui.MainTab
import dev.terashima.yomitorirss.ui.YomitoriApp
import dev.terashima.yomitorirss.ui.YomitoriTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
  companion object {
    const val ACTION_ADD_SHARED_URL_TO_LIBRARY =
      "dev.terashima.yomitorirss.action.ADD_SHARED_URL_TO_LIBRARY"
  }

  private val appViewModel: AppViewModel by viewModels()
  private val dependencies: MainActivityDependencies by lazy(LazyThreadSafetyMode.NONE) {
    val provider = application as? MainActivityDependenciesProvider
      ?: error("Application must implement MainActivityDependenciesProvider")
    provider.mainActivityDependencies
  }
  private val appLockPreferences by lazy(LazyThreadSafetyMode.NONE) {
    AppLockPreferences(this)
  }

  private var showingCrashDiagnostics = false
  private var appLockEnabled by mutableStateOf(false)
  private var appUnlocked by mutableStateOf(true)
  private var appLockPromptShowing = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    appLockEnabled = appLockPreferences.enabled
    appUnlocked = !appLockEnabled
    val crashReport = StartupCrashStore.peek(this)
    showingCrashDiagnostics = crashReport != null

    setContent {
      YomitoriTheme {
        when {
          appLockEnabled && !appUnlocked -> AppLockContent(onUnlock = ::requestAppUnlock)
          crashReport != null -> CrashDiagnosticsContent(crashReport)
          else -> MainContent()
        }
      }
    }

    if (crashReport == null) {
      consumeSharedLibrary(intent)
      consumeSharedBookmark(intent)
      consumeTaskWidget(intent)
      consumeWidgetArticle(intent)
    }
  }

  override fun onStart() {
    super.onStart()
    if (appLockEnabled && !appUnlocked && !showingCrashDiagnostics) {
      requestAppUnlock()
    } else if (appLockEnabled && !appUnlocked && showingCrashDiagnostics) {
      requestAppUnlock()
    }
  }

  override fun onStop() {
    if (appLockEnabled && !isChangingConfigurations && !appLockPromptShowing) {
      appUnlocked = false
    }
    super.onStop()
  }

  @Composable
  private fun MainContent() {
    var showWebServer by remember { mutableStateOf(false) }
    var webServerPermissionError by remember { mutableStateOf<String?>(null) }
    val lanServerState by dependencies.lanWebServerController.state.collectAsState()
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
      ActivityResultContracts.RequestPermission(),
    ) { granted ->
      if (granted) {
        webServerPermissionError = null
        dependencies.lanWebServerController.start()
      } else {
        webServerPermissionError = "通知を許可しないとWebサーバを起動できません。"
      }
    }

    YomitoriApp(
      appViewModel = appViewModel,
      routeDependencies = dependencies.routeDependencies,
      biometricLockEnabled = appLockEnabled,
      onBiometricLockEnabledChange = ::setBiometricLockEnabled,
      onOpenArticle = ::openArticle,
      onOpenWebServer = {
        webServerPermissionError = null
        showWebServer = true
      },
      onExitApp = ::finish,
    )

    if (showWebServer) {
      WebServerDialog(
        state = lanServerState.copy(
          error = webServerPermissionError ?: lanServerState.error,
        ),
        onDismiss = { showWebServer = false },
        onStart = {
          webServerPermissionError = null
          if (
            ContextCompat.checkSelfPermission(
              this,
              Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
          ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
          } else {
            dependencies.lanWebServerController.start()
          }
        },
        onStop = {
          webServerPermissionError = null
          dependencies.lanWebServerController.stop()
        },
      )
    }
  }

  @Composable
  private fun AppLockContent(onUnlock: () -> Unit) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(24.dp),
    ) {
      Text("アプリはロックされています", style = MaterialTheme.typography.headlineSmall)
      Text(
        "生体認証または端末の画面ロックで認証してください。",
        modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
      )
      Button(onClick = onUnlock) {
        Text("ロックを解除")
      }
    }
  }

  @Composable
  private fun CrashDiagnosticsContent(report: String) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(24.dp),
    ) {
      Text("起動エラーを検出しました", style = MaterialTheme.typography.headlineSmall)
      Text(
        "クラッシュを繰り返さないため通常の初期化を停止しています。下の情報を共有すると原因を特定できます。",
        modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
      )
      Button(onClick = { copyCrashReport(report) }) {
        Text("クラッシュ情報をコピー")
      }
      Button(
        onClick = {
          StartupCrashStore.clear(this@MainActivity)
          recreate()
        },
        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
      ) {
        Text("通常起動を再試行")
      }
      SelectionContainer {
        Text(report, style = MaterialTheme.typography.bodySmall)
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    if (showingCrashDiagnostics) return
    consumeSharedLibrary(intent)
    consumeSharedBookmark(intent)
    consumeTaskWidget(intent)
    consumeWidgetArticle(intent)
  }

  private fun setBiometricLockEnabled(enabled: Boolean) {
    if (!enabled) {
      appLockPreferences.enabled = false
      appLockEnabled = false
      appUnlocked = true
      return
    }

    val biometricManager = getSystemService(BiometricManager::class.java)
    if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) !=
      BiometricManager.BIOMETRIC_SUCCESS
    ) {
      Toast.makeText(this, "端末に生体認証を登録してから有効にしてください", Toast.LENGTH_LONG).show()
      return
    }
    requestAuthentication(enableAfterSuccess = true)
  }

  private fun requestAppUnlock() {
    if (!appLockEnabled || appUnlocked) return
    requestAuthentication(enableAfterSuccess = false)
  }

  private fun requestAuthentication(enableAfterSuccess: Boolean) {
    if (appLockPromptShowing) return
    appLockPromptShowing = true

    val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
      BiometricManager.Authenticators.DEVICE_CREDENTIAL
    val prompt = BiometricPrompt.Builder(this)
      .setTitle(if (enableAfterSuccess) "生体認証ロックを有効にする" else "アプリのロックを解除")
      .setSubtitle("生体認証または端末の画面ロックで認証")
      .setAllowedAuthenticators(authenticators)
      .build()

    prompt.authenticate(
      CancellationSignal(),
      mainExecutor,
      object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
          appLockPromptShowing = false
          if (enableAfterSuccess) {
            appLockPreferences.enabled = true
            appLockEnabled = true
            appUnlocked = true
            Toast.makeText(this@MainActivity, "生体認証ロックを有効にしました", Toast.LENGTH_SHORT).show()
          } else {
            appUnlocked = true
          }
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
          appLockPromptShowing = false
          if (enableAfterSuccess && errorCode != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED) {
            Toast.makeText(this@MainActivity, errString, Toast.LENGTH_LONG).show()
          }
        }
      },
    )
  }

  private fun copyCrashReport(report: String) {
    val clipboard = getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("Yomitori crash report", report))
    Toast.makeText(this, "クラッシュ情報をコピーしました", Toast.LENGTH_SHORT).show()
  }

  private fun openArticle(article: Article) {
    if (!openWebContentInCustomTab(article.url)) {
      Toast.makeText(this, "記事を開けませんでした", Toast.LENGTH_LONG).show()
    }
  }

  private fun consumeTaskWidget(incoming: Intent) {
    val tab = widgetLaunchTab(incoming.action) ?: return
    incoming.action = null
    appViewModel.selectTab(tab)
  }

  private fun consumeWidgetArticle(incoming: Intent) {
    if (incoming.action != UnreadArticlesWidgetProvider.ACTION_OPEN_ARTICLE) return
    val url = incoming.getStringExtra(UnreadArticlesWidgetProvider.EXTRA_ARTICLE_URL)
      ?.trim()
      .orEmpty()
    incoming.action = null
    incoming.removeExtra(UnreadArticlesWidgetProvider.EXTRA_ARTICLE_URL)
    if (url.isBlank()) return

    if (!openWebContentInCustomTab(url)) {
      Toast.makeText(this, "記事を開けませんでした", Toast.LENGTH_LONG).show()
    }
  }

  private fun consumeSharedLibrary(incoming: Intent) {
    if (incoming.action != ACTION_ADD_SHARED_URL_TO_LIBRARY) return

    val shared = parseSharedBookmark(
      text = incoming.getCharSequenceExtra(Intent.EXTRA_TEXT),
      subject = incoming.getCharSequenceExtra(Intent.EXTRA_SUBJECT),
    )
    incoming.action = null
    incoming.removeExtra(Intent.EXTRA_TEXT)
    incoming.removeExtra(Intent.EXTRA_SUBJECT)

    if (shared == null) {
      Toast.makeText(this, "共有内容に http/https の URL がありません", Toast.LENGTH_LONG).show()
      return
    }

    lifecycleScope.launch {
      runCatching {
        withContext(Dispatchers.IO) {
          dependencies.addSharedWebBook(shared.url, shared.title)
        }
      }.onSuccess { book ->
        appViewModel.selectTab(MainTab.LIBRARY)
        Toast.makeText(
          this@MainActivity,
          "「${book.title}」を蔵書へ追加しました",
          Toast.LENGTH_SHORT,
        ).show()
      }.onFailure { error ->
        val message = error.message?.takeIf(String::isNotBlank) ?: "蔵書への追加に失敗しました"
        Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
      }
    }
  }

  private fun consumeSharedBookmark(incoming: Intent) {
    if (incoming.action != Intent.ACTION_SEND || incoming.type != "text/plain") return

    val bookmark = parseSharedBookmark(
      text = incoming.getCharSequenceExtra(Intent.EXTRA_TEXT),
      subject = incoming.getCharSequenceExtra(Intent.EXTRA_SUBJECT),
    )
    incoming.action = null
    incoming.removeExtra(Intent.EXTRA_TEXT)
    incoming.removeExtra(Intent.EXTRA_SUBJECT)

    if (bookmark == null) {
      Toast.makeText(this, "共有内容にブックマークできるURLがありません", Toast.LENGTH_LONG).show()
      return
    }

    lifecycleScope.launch {
      runCatching {
        withContext(Dispatchers.IO) {
          dependencies.saveSharedArticle(bookmark.url, bookmark.title, bookmark.sourceTitle)
        }
      }.onSuccess { result ->
        appViewModel.selectTab(MainTab.SAVED)
        val message = when (result) {
          BookmarkSaveResult.ADDED -> "ブックマークに追加しました"
          BookmarkSaveResult.ALREADY_BOOKMARKED -> "すでにブックマークされています"
        }
        Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
      }.onFailure { error ->
        val message = error.message?.takeIf(String::isNotBlank) ?: "ブックマークを保存できませんでした"
        Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
      }
    }
  }
}

internal fun widgetLaunchTab(action: String?): MainTab? =
  when (action) {
    TaskWidgetProvider.ACTION_OPEN_TASKS -> MainTab.TASKS
    else -> null
  }
