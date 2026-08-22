package dev.terashima.yomitorirss.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import dev.terashima.yomitorirss.feature.x.XViewerRoute
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

  when (selectedTab) {
    MainTab.INTEGRATED -> IntegratedRoute(
      modifier = modifier,
      rssViewModel = rssViewModel,
      redditViewModel = redditViewModel,
      feedViewModel = feedViewModel,
      mailViewModel = mailViewModel,
      youtubeViewModelFactory = routeDependencies.youtubeViewModelFactory,
      onOpenArticle = onOpenArticle,
      onSummarize = summarize,
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
    MainTab.X -> XViewerRoute(
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
      initialBackgroundFetchWifiOnly = routeDependencies.backgroundFetchWifiOnly(),
      onBackgroundFetchWifiOnlyChange = routeDependencies::setBackgroundFetchWifiOnly,
      onOpenWebServer = onOpenWebServer,
      onNavigate = appViewModel::selectTab,
    )
  }
}
