package dev.terashima.yomitorirss

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
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
import androidx.compose.runtime.LaunchedEffect
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
import dev.terashima.yomitorirss.feature.backup.BackupViewModel
import dev.terashima.yomitorirss.feature.bookmark.BookmarkSaveResult
import dev.terashima.yomitorirss.feature.bookmark.BookmarkViewModel
import dev.terashima.yomitorirss.feature.chat.ChatViewModel
import dev.terashima.yomitorirss.feature.mail.MailViewModel
import dev.terashima.yomitorirss.feature.mail.data.GmailAuthorizationOutcome
import dev.terashima.yomitorirss.feature.navigation.AppViewModel
import dev.terashima.yomitorirss.feature.navigation.MainTab
import dev.terashima.yomitorirss.feature.reddit.RedditViewModel
import dev.terashima.yomitorirss.feature.reddit.isRedditArticle
import dev.terashima.yomitorirss.feature.reddit.isRedditFeedUrl
import dev.terashima.yomitorirss.feature.reddit.redditCommunityFeedUrl
import dev.terashima.yomitorirss.feature.reddit.redditThreadId
import dev.terashima.yomitorirss.feature.rss.FeedViewModel
import dev.terashima.yomitorirss.feature.rss.RssViewModel
import dev.terashima.yomitorirss.feature.settings.AiSettingsViewModel
import dev.terashima.yomitorirss.feature.summary.SummaryViewModel
import dev.terashima.yomitorirss.feature.web.LanServerStatus
import dev.terashima.yomitorirss.feature.web.WebServerDialog
import dev.terashima.yomitorirss.feature.web.data.LanWebServerService
import dev.terashima.yomitorirss.feature.widget.UnreadArticlesWidgetProvider
import dev.terashima.yomitorirss.feature.widget.UnreadArticlesWidgetUpdater
import dev.terashima.yomitorirss.ui.YomitoriApp
import dev.terashima.yomitorirss.ui.YomitoriTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
  private val appViewModel: AppViewModel by viewModels()
  private val rssViewModel: RssViewModel by viewModels {
    val container = (application as YomitoriApplication).container
    RssViewModel.Factory(
      articleRepository = container.articleRepository,
      bookmarkRepository = container.bookmarkRepository,
      backupChangeScheduler = container.backupChangeScheduler,
      summaryRepository = container.summaryRepository,
      articleSelector = { article -> !article.isRedditArticle() },
    )
  }
  private val redditViewModel: RedditViewModel by viewModels {
    val container = (application as YomitoriApplication).container
    RedditViewModel.Factory(
      redditRepository = container.redditRepository,
      articleRepository = container.articleRepository,
      bookmarkRepository = container.bookmarkRepository,
      backupChangeScheduler = container.backupChangeScheduler,
    )
  }
  private val feedViewModel: FeedViewModel by viewModels {
    val container = (application as YomitoriApplication).container
    FeedViewModel.Factory(
      repository = container.feedRepository,
      refreshFeeds = container.refreshFeedsUseCase,
      imports = container.feedImportRepository,
      backupChangeScheduler = container.backupChangeScheduler,
      feedSelector = { feed -> !isRedditFeedUrl(feed.feedUrl) },
      canAddInput = { input ->
        redditCommunityFeedUrl(input) == null &&
          redditThreadId(input) == null &&
          !isRedditFeedUrl(input)
      },
    )
  }
  private val bookmarkViewModel: BookmarkViewModel by viewModels {
    val container = (application as YomitoriApplication).container
    BookmarkViewModel.Factory(
      articleRepository = container.articleRepository,
      bookmarkRepository = container.bookmarkRepository,
      imports = container.bookmarkImportRepository,
      backupChangeScheduler = container.backupChangeScheduler,
    )
  }
  private val mailViewModel: MailViewModel by viewModels {
    MailViewModel.Factory((application as YomitoriApplication).container.mailRepository)
  }
  private val summaryViewModel: SummaryViewModel by viewModels {
    SummaryViewModel.Factory((application as YomitoriApplication).container.summaryRepository)
  }
  private val backupViewModel: BackupViewModel by viewModels {
    BackupViewModel.Factory((application as YomitoriApplication).container.backupRepository)
  }
  private val aiSettingsViewModel: AiSettingsViewModel by viewModels {
    AiSettingsViewModel.Factory((application as YomitoriApplication).container.aiModelRepository)
  }
  private val chatViewModel: ChatViewModel by viewModels {
    val container = (application as YomitoriApplication).container
    ChatViewModel.Factory(container.chatRepository, container.chatGenerator)
  }

  private var showingCrashDiagnostics = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    StartupCrashStore.peek(this)?.let { report ->
      showingCrashDiagnostics = true
      showCrashDiagnostics(report)
      return
    }

    val startup = runCatching {
      StartupViewModels(
        app = appViewModel,
        rss = rssViewModel,
        reddit = redditViewModel,
        feed = feedViewModel,
        bookmark = bookmarkViewModel,
        mail = mailViewModel,
        summary = summaryViewModel,
        backup = backupViewModel,
        aiSettings = aiSettingsViewModel,
        chat = chatViewModel,
      )
    }
    if (startup.isFailure) {
      val error = startup.exceptionOrNull() ?: IllegalStateException("Unknown startup failure")
      StartupCrashStore.record(this, Thread.currentThread().name, error)
      showingCrashDiagnostics = true
      showCrashDiagnostics(StartupCrashStore.peek(this) ?: error.stackTraceToString())
      return
    }
    val viewModels = startup.getOrThrow()

    setContent {
      YomitoriTheme {
        var showWebServer by remember { mutableStateOf(false) }
        val lanServerState by LanServerStatus.state.collectAsState()
        val rssState by viewModels.rss.state.collectAsState()
        val container = (application as YomitoriApplication).container
        val gmailAuthorization = container.gmailAuthorizationManager
        val notificationPermissionLauncher = rememberLauncherForActivityResult(
          ActivityResultContracts.RequestPermission(),
        ) { granted ->
          if (granted) {
            LanWebServerService.start(applicationContext)
          } else {
            LanServerStatus.reportError("通知を許可しないとWebサーバを起動できません。")
          }
        }
        val mailAuthorizationLauncher = rememberLauncherForActivityResult(
          ActivityResultContracts.StartIntentSenderForResult(),
        ) { result ->
          val data = result.data
          if (data == null) {
            Toast.makeText(
              this@MainActivity,
              "Gmail の認証結果を取得できませんでした",
              Toast.LENGTH_LONG,
            ).show()
            return@rememberLauncherForActivityResult
          }
          lifecycleScope.launch {
            runCatching { gmailAuthorization.resultFromIntent(data) }
              .onSuccess { account ->
                viewModels.mail.connectAuthorizedAccount(
                  email = account.email,
                  displayName = account.displayName,
                  accessToken = account.accessToken,
                )
              }
              .onFailure { error ->
                Toast.makeText(
                  this@MainActivity,
                  error.message ?: "Gmail の認証に失敗しました",
                  Toast.LENGTH_LONG,
                ).show()
              }
          }
        }
        val requestMailAccount: () -> Unit = {
          lifecycleScope.launch {
            runCatching { gmailAuthorization.requestAccount() }
              .onSuccess { outcome ->
                when (outcome) {
                  is GmailAuthorizationOutcome.Authorized -> {
                    val account = outcome.account
                    viewModels.mail.connectAuthorizedAccount(
                      email = account.email,
                      displayName = account.displayName,
                      accessToken = account.accessToken,
                    )
                  }

                  is GmailAuthorizationOutcome.RequiresResolution -> {
                    mailAuthorizationLauncher.launch(
                      IntentSenderRequest.Builder(outcome.pendingIntent.intentSender).build(),
                    )
                  }
                }
              }
              .onFailure { error ->
                Toast.makeText(
                  this@MainActivity,
                  error.message ?: "Gmail の認証を開始できませんでした",
                  Toast.LENGTH_LONG,
                ).show()
              }
          }
        }

        LaunchedEffect(rssState.unread) {
          withContext(Dispatchers.IO) {
            runCatching { UnreadArticlesWidgetUpdater.updateAll(applicationContext) }
          }
        }

        YomitoriApp(
          appViewModel = viewModels.app,
          rssViewModel = viewModels.rss,
          redditViewModel = viewModels.reddit,
          feedViewModel = viewModels.feed,
          bookmarkViewModel = viewModels.bookmark,
          mailViewModel = viewModels.mail,
          summaryViewModel = viewModels.summary,
          backupViewModel = viewModels.backup,
          aiSettingsViewModel = viewModels.aiSettings,
          chatViewModel = viewModels.chat,
          onOpenArticle = ::openArticle,
          onOpenWebServer = { showWebServer = true },
          onAddMailAccount = requestMailAccount,
          onExitApp = ::finish,
        )

        if (showWebServer) {
          WebServerDialog(
            state = lanServerState,
            onDismiss = { showWebServer = false },
            onStart = {
              if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                  this,
                  Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
              ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
              } else {
                LanWebServerService.start(applicationContext)
              }
            },
            onStop = { LanWebServerService.stop(applicationContext) },
          )
        }
      }
    }
    consumeSharedBookmark(intent)
    consumeWidgetArticle(intent)
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    if (showingCrashDiagnostics) return
    consumeSharedBookmark(intent)
    consumeWidgetArticle(intent)
  }

  override fun onResume() {
    super.onResume()
    if (!showingCrashDiagnostics) bookmarkViewModel.refresh()
  }

  private fun showCrashDiagnostics(report: String) {
    setContent {
      YomitoriTheme {
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
    }
  }

  private fun copyCrashReport(report: String) {
    val clipboard = getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("Yomitori crash report", report))
    Toast.makeText(this, "クラッシュ情報をコピーしました", Toast.LENGTH_SHORT).show()
  }

  private fun openArticle(article: Article) {
    runCatching {
      startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(article.url)))
    }.onFailure {
      Toast.makeText(this, "記事を開けませんでした", Toast.LENGTH_LONG).show()
    }
  }

  private fun consumeWidgetArticle(incoming: Intent) {
    if (incoming.action != UnreadArticlesWidgetProvider.ACTION_OPEN_ARTICLE) return
    val url = incoming.getStringExtra(UnreadArticlesWidgetProvider.EXTRA_ARTICLE_URL)
      ?.trim()
      .orEmpty()
    incoming.action = null
    incoming.removeExtra(UnreadArticlesWidgetProvider.EXTRA_ARTICLE_URL)
    if (url.isBlank()) return

    runCatching {
      startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }.onFailure {
      Toast.makeText(this, "記事を開けませんでした", Toast.LENGTH_LONG).show()
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

    val container = (application as YomitoriApplication).container
    lifecycleScope.launch {
      runCatching {
        withContext(Dispatchers.IO) {
          container.bookmarkRepository.saveSharedArticle(bookmark.url, bookmark.title, bookmark.sourceTitle)
        }
      }.onSuccess { result ->
        if (result == BookmarkSaveResult.ADDED) container.backupChangeScheduler.scheduleAfterChange()
        bookmarkViewModel.selectTag(null)
        bookmarkViewModel.selectFolder(null)
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

  private data class StartupViewModels(
    val app: AppViewModel,
    val rss: RssViewModel,
    val reddit: RedditViewModel,
    val feed: FeedViewModel,
    val bookmark: BookmarkViewModel,
    val mail: MailViewModel,
    val summary: SummaryViewModel,
    val backup: BackupViewModel,
    val aiSettings: AiSettingsViewModel,
    val chat: ChatViewModel,
  )
}
