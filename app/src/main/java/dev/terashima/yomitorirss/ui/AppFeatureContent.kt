package dev.terashima.yomitorirss.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.terashima.yomitorirss.AppRouteDependencies
import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.asset.AssetRoute
import dev.terashima.yomitorirss.feature.backup.BackupViewModel
import dev.terashima.yomitorirss.feature.bookmark.BookmarkEditController
import dev.terashima.yomitorirss.feature.bookmark.BookmarkRoute
import dev.terashima.yomitorirss.feature.bookmark.BookmarkViewModel
import dev.terashima.yomitorirss.feature.calendar.CalendarRoute
import dev.terashima.yomitorirss.feature.chat.ChatRoute
import dev.terashima.yomitorirss.feature.chat.ChatViewModel
import dev.terashima.yomitorirss.feature.game.GameRoute
import dev.terashima.yomitorirss.feature.health.HealthRoute
import dev.terashima.yomitorirss.feature.integrated.IntegratedRoute
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeRoute
import dev.terashima.yomitorirss.feature.library.LibraryRoute
import dev.terashima.yomitorirss.feature.mail.MailViewModel
import dev.terashima.yomitorirss.feature.navigation.AppViewModel
import dev.terashima.yomitorirss.feature.navigation.MainTab
import dev.terashima.yomitorirss.feature.reddit.RedditRoute
import dev.terashima.yomitorirss.feature.reddit.RedditRouteController
import dev.terashima.yomitorirss.feature.reddit.RedditViewModel
import dev.terashima.yomitorirss.feature.rss.FeedRoute
import dev.terashima.yomitorirss.feature.rss.FeedViewModel
import dev.terashima.yomitorirss.feature.rss.RssRoute
import dev.terashima.yomitorirss.feature.rss.RssRouteController
import dev.terashima.yomitorirss.feature.rss.RssViewModel
import dev.terashima.yomitorirss.feature.settings.AiSettingsViewModel
import dev.terashima.yomitorirss.feature.summary.SummaryViewModel
import dev.terashima.yomitorirss.feature.task.TaskScreen
import dev.terashima.yomitorirss.feature.workout.WorkoutRoute
import dev.terashima.yomitorirss.feature.x.XViewerCssRepository
import dev.terashima.yomitorirss.feature.x.XViewerCssSettingsSheet
import dev.terashima.yomitorirss.feature.x.XViewerScreen
import dev.terashima.yomitorirss.feature.youtube.YouTubeRoute

@Composable
internal fun AppFeatureContent(
  selectedTab: MainTab,
  modifier: Modifier,
  appViewModel: AppViewModel,
  routeDependencies: AppRouteDependencies,
  rssController: RssRouteController,
  redditController: RedditRouteController,
  bookmarkEditController: BookmarkEditController,
  onOpenArticle: (Article) -> Unit,
  onOpenWebServer: () -> Unit,
) {
  val rssViewModel: RssViewModel = viewModel(factory = routeDependencies.rssViewModelFactory)
  val redditViewModel: RedditViewModel = viewModel(factory = routeDependencies.redditViewModelFactory)
  val feedViewModel: FeedViewModel = viewModel(factory = routeDependencies.feedViewModelFactory)
  val bookmarkViewModel: BookmarkViewModel = viewModel(factory = routeDependencies.bookmarkViewModelFactory)
  val mailViewModel: MailViewModel = viewModel(factory = routeDependencies.mailViewModelFactory)
  val summaryViewModel: SummaryViewModel = viewModel(factory = routeDependencies.summaryViewModelFactory)
  val backupViewModel: BackupViewModel = viewModel(factory = routeDependencies.backupViewModelFactory)
  val aiSettingsViewModel: AiSettingsViewModel = viewModel(factory = routeDependencies.aiSettingsViewModelFactory)
  val chatViewModel: ChatViewModel = viewModel(factory = routeDependencies.chatViewModelFactory)
  val summarize: (Article) -> Unit = { article -> summaryViewModel.summarize(article) }
  val bookmarkCsvImportLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument(),
  ) { uri -> uri?.toString()?.let(bookmarkViewModel::importCsv) }
  val bookmarkHtmlImportLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument(),
  ) { uri -> uri?.toString()?.let(bookmarkViewModel::importHtml) }

  LaunchedEffect(bookmarkViewModel) {
    bookmarkViewModel.refresh()
  }
  LaunchedEffect(selectedTab) {
    if (selectedTab == MainTab.SAVED) {
      bookmarkViewModel.selectTag(null)
      bookmarkViewModel.selectFolder(null)
    }
  }

  when (selectedTab) {
    MainTab.INTEGRATED -> IntegratedRoute(
      modifier = modifier,
      rssViewModel = rssViewModel,
      redditViewModel = redditViewModel,
      feedViewModel = feedViewModel,
      mailViewModel = mailViewModel,
      youtubeViewModelFactory = routeDependencies.youtubeViewModelFactory,
      onOpenArticle = onOpenArticle,
      onOpenMail = { thread ->
        mailViewModel.openThread(thread)
        appViewModel.selectTab(MainTab.MAIL)
      },
    )

    MainTab.UNREAD,
    MainTab.READ_LATER -> RssRoute(
      modifier = modifier,
      tab = requireNotNull(selectedTab.rssTab()),
      rssViewModel = rssViewModel,
      feedViewModel = feedViewModel,
      controller = rssController,
      onOpen = onOpenArticle,
      onSummarize = summarize,
      onEditTags = bookmarkEditController::editTags,
      onMoveFolder = bookmarkEditController::moveFolder,
    )

    MainTab.REDDIT_UNREAD,
    MainTab.REDDIT_READ_LATER,
    MainTab.REDDIT_SUBSCRIPTIONS -> RedditRoute(
      modifier = modifier,
      tab = requireNotNull(selectedTab.redditTab()),
      redditViewModel = redditViewModel,
      controller = redditController,
      onOpen = onOpenArticle,
      onSummarize = summarize,
    )

    MainTab.SAVED,
    MainTab.FOLDERS,
    MainTab.TAGS,
    MainTab.BOOKMARK_IMPORT -> BookmarkRoute(
      modifier = modifier,
      tab = requireNotNull(selectedTab.bookmarkTab()),
      bookmarkViewModel = bookmarkViewModel,
      editController = bookmarkEditController,
      onOpen = onOpenArticle,
      onSummarize = summarize,
      onImportCsv = {
        bookmarkCsvImportLauncher.launch(
          arrayOf("text/csv", "text/comma-separated-values", "application/csv", "text/plain"),
        )
      },
      onImportHtml = {
        bookmarkHtmlImportLauncher.launch(
          arrayOf("text/html", "application/xhtml+xml", "text/plain"),
        )
      },
      onImportCompleted = { appViewModel.selectTab(MainTab.SAVED) },
    )

    MainTab.LIBRARY -> LibraryRoute(
      dependencies = routeDependencies.library,
      modifier = modifier,
    )
    MainTab.KNOWLEDGE -> KnowledgeRoute(
      viewModelFactory = routeDependencies.knowledgeViewModelFactory,
      modifier = modifier,
    )
    MainTab.ASSETS -> AssetRoute(
      viewModelFactory = routeDependencies.assetViewModelFactory,
      modifier = modifier,
    )
    MainTab.MAIL -> MailRouteHost(
      modifier = modifier,
      routeDependencies = routeDependencies,
    )
    MainTab.YOUTUBE -> YouTubeRoute(
      viewModelFactory = routeDependencies.youtubeViewModelFactory,
      modifier = modifier,
    )
    MainTab.X -> XFeatureHost(
      repository = routeDependencies.xViewerCssRepository,
      modifier = modifier,
    )
    MainTab.TASKS -> TaskScreen(
      viewModelFactory = routeDependencies.taskViewModelFactory,
      onTasksChanged = routeDependencies.updateTaskWidget,
      modifier = modifier,
    )
    MainTab.CALENDAR -> CalendarRoute(
      viewModelFactory = routeDependencies.calendarViewModelFactory,
      modifier = modifier,
    )
    MainTab.GAME -> GameRoute(modifier = modifier)
    MainTab.HEALTH -> HealthRoute(
      viewModelFactory = routeDependencies.health.viewModelFactory,
      readPermissions = routeDependencies.health.readPermissions,
      modifier = modifier,
    )
    MainTab.WORKOUT -> WorkoutRoute(
      viewModelFactory = routeDependencies.workoutViewModelFactory,
      modifier = modifier,
    )
    MainTab.AI_CHAT -> ChatRoute(modifier = modifier, chatViewModel = chatViewModel)
    MainTab.FEEDS -> FeedRoute(
      modifier = modifier,
      feedViewModel = feedViewModel,
      controller = rssController,
      onFeedReady = { appViewModel.selectTab(MainTab.FEEDS) },
    )
    MainTab.SETTINGS -> SettingsRoute(
      modifier = modifier,
      backupViewModel = backupViewModel,
      aiSettingsViewModel = aiSettingsViewModel,
      aiTaskQueueRepository = routeDependencies.aiTaskQueueRepository,
      onOpenWebServer = onOpenWebServer,
      onNavigate = appViewModel::selectTab,
    )
  }
}

@Composable
private fun XFeatureHost(
  repository: XViewerCssRepository,
  modifier: Modifier = Modifier,
) {
  var showCssSettings by remember { mutableStateOf(false) }

  Box(modifier = modifier) {
    XViewerScreen(
      repository = repository,
      modifier = Modifier.fillMaxSize(),
    )
    Surface(
      modifier = Modifier
        .align(Alignment.TopEnd)
        .windowInsetsPadding(
          WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End),
        )
        .padding(8.dp),
      shape = MaterialTheme.shapes.large,
      color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
      tonalElevation = 4.dp,
    ) {
      IconButton(onClick = { showCssSettings = true }) {
        Icon(Icons.Default.Settings, contentDescription = "X カスタム CSS 設定")
      }
    }
  }

  if (showCssSettings) {
    XViewerCssSettingsSheet(
      repository = repository,
      onDismiss = { showCssSettings = false },
    )
  }
}
