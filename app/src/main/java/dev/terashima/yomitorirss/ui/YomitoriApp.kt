package dev.terashima.yomitorirss.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import dev.terashima.yomitorirss.AppRouteDependencies
import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.backup.BackupViewModel
import dev.terashima.yomitorirss.feature.bookmark.BookmarkEditHost
import dev.terashima.yomitorirss.feature.bookmark.BookmarkViewModel
import dev.terashima.yomitorirss.feature.bookmark.rememberBookmarkEditController
import dev.terashima.yomitorirss.feature.chat.ChatViewModel
import dev.terashima.yomitorirss.feature.mail.MailViewModel
import dev.terashima.yomitorirss.feature.navigation.AppViewModel
import dev.terashima.yomitorirss.feature.navigation.MainTab
import dev.terashima.yomitorirss.feature.reddit.RedditViewModel
import dev.terashima.yomitorirss.feature.reddit.rememberRedditRouteController
import dev.terashima.yomitorirss.feature.rss.FeedViewModel
import dev.terashima.yomitorirss.feature.rss.RssViewModel
import dev.terashima.yomitorirss.feature.rss.rememberRssRouteController
import dev.terashima.yomitorirss.feature.settings.AiSettingsViewModel
import dev.terashima.yomitorirss.feature.summary.SummaryViewModel
import kotlinx.coroutines.launch

@Composable
fun YomitoriApp(
  appViewModel: AppViewModel,
  rssViewModel: RssViewModel,
  redditViewModel: RedditViewModel,
  feedViewModel: FeedViewModel,
  bookmarkViewModel: BookmarkViewModel,
  mailViewModel: MailViewModel,
  summaryViewModel: SummaryViewModel,
  backupViewModel: BackupViewModel,
  aiSettingsViewModel: AiSettingsViewModel,
  chatViewModel: ChatViewModel,
  routeDependencies: AppRouteDependencies,
  onOpenArticle: (Article) -> Unit,
  onOpenWebServer: () -> Unit,
  onAddMailAccount: () -> Unit,
  onExitApp: () -> Unit,
) {
  val appState by appViewModel.state.collectAsState()
  val selectedTab = appState.selectedTab
  val selectedSection = selectedTab.appSection()
  val snackbarHostState = remember { SnackbarHostState() }
  val drawerState = rememberDrawerState(DrawerValue.Closed)
  val scope = rememberCoroutineScope()
  val rssController = rememberRssRouteController()
  val redditController = rememberRedditRouteController()
  val bookmarkEditController = rememberBookmarkEditController()
  val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }

  FeatureMessageEffects(
    snackbarHostState = snackbarHostState,
    appViewModel = appViewModel,
    rssViewModel = rssViewModel,
    redditViewModel = redditViewModel,
    feedViewModel = feedViewModel,
    bookmarkViewModel = bookmarkViewModel,
    summaryViewModel = summaryViewModel,
    backupViewModel = backupViewModel,
    aiSettingsViewModel = aiSettingsViewModel,
  )

  ModalNavigationDrawer(
    drawerState = drawerState,
    gesturesEnabled = drawerState.isOpen,
    drawerContent = {
      AppDrawerContent(
        selectedSection = selectedSection,
        onSelectSection = { section ->
          appViewModel.selectTab(section.defaultTab())
          scope.launch { drawerState.close() }
        },
      )
    },
  ) {
    BackHandler {
      when (rootBackAction(drawerState.isOpen)) {
        RootBackAction.OPEN_DRAWER -> openDrawer()
        RootBackAction.EXIT_APP -> onExitApp()
      }
    }

    Scaffold(
      snackbarHost = { SnackbarHost(snackbarHostState) },
      contentWindowInsets = if (selectedTab == MainTab.X) {
        WindowInsets(0, 0, 0, 0)
      } else {
        ScaffoldDefaults.contentWindowInsets
      },
      topBar = {
        AppTopBar(
          selectedTab = selectedTab,
          rssViewModel = rssViewModel,
          redditViewModel = redditViewModel,
          feedViewModel = feedViewModel,
          rssController = rssController,
          redditController = redditController,
          onOpenDrawer = openDrawer,
        )
      },
      bottomBar = {
        AppBottomBar(
          selectedTab = selectedTab,
          onSelectTab = appViewModel::selectTab,
        )
      },
    ) { padding ->
      AppFeatureContent(
        selectedTab = selectedTab,
        modifier = Modifier.fillMaxSize().padding(padding),
        appViewModel = appViewModel,
        rssViewModel = rssViewModel,
        redditViewModel = redditViewModel,
        feedViewModel = feedViewModel,
        bookmarkViewModel = bookmarkViewModel,
        mailViewModel = mailViewModel,
        summaryViewModel = summaryViewModel,
        backupViewModel = backupViewModel,
        aiSettingsViewModel = aiSettingsViewModel,
        chatViewModel = chatViewModel,
        routeDependencies = routeDependencies,
        rssController = rssController,
        redditController = redditController,
        bookmarkEditController = bookmarkEditController,
        onOpenArticle = onOpenArticle,
        onOpenWebServer = onOpenWebServer,
        onAddMailAccount = onAddMailAccount,
        onOpenDrawer = openDrawer,
      )
    }
  }

  BookmarkEditHost(
    bookmarkViewModel = bookmarkViewModel,
    controller = bookmarkEditController,
  )
  SummaryOverlay(
    summaryViewModel = summaryViewModel,
    aiSettingsViewModel = aiSettingsViewModel,
  )
}
