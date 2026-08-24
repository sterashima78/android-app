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
import dev.terashima.yomitorirss.feature.chat.ChatRoute
import dev.terashima.yomitorirss.feature.chat.ChatViewModel
import dev.terashima.yomitorirss.feature.health.HealthRoute
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeRoute
import dev.terashima.yomitorirss.feature.mail.MailViewModel
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
import dev.terashima.yomitorirss.feature.task.TaskRoute
import dev.terashima.yomitorirss.feature.workout.WorkoutRoute
import dev.terashima.yomitorirss.feature.x.XViewerRoute

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
  onGameFullscreenChange: (Boolean) -> Unit,
) {
  when (selectedTab) {
    MainTab.INTEGRATED -> {
      val rssViewModel: RssViewModel = viewModel(factory = routeDependencies.rssViewModelFactory)
      val redditViewModel: RedditViewModel = viewModel(factory = routeDependencies.redditViewModelFactory)
      val feedViewModel: FeedViewModel = viewModel(factory = routeDependencies.feedViewModelFactory)
      val mailViewModel: MailViewModel = viewModel(factory = routeDependencies.mailViewModelFactory)
      val summaryViewModel: SummaryViewModel = viewModel(factory = routeDependencies.summaryViewModelFactory)
      IntegratedRoute(
        modifier = modifier,
        rssViewModel = rssViewModel,
        redditViewModel = redditViewModel,
        feedViewModel = feedViewModel,
        mailViewModel = mailViewModel,
        youtubeViewModelFactory = routeDependencies.youtubeViewModelFactory,
        onOpenArticle = onOpenArticle,
        onSummarize = { article -> summaryViewModel.summarize(article) },
        onOpenMail = { thread ->
          mailViewModel.openThread(thread)
          appViewModel.selectTab(MainTab.MAIL)
        },
      )
    }

    MainTab.UNREAD,
    MainTab.READ_LATER -> {
      val rssViewModel: RssViewModel = viewModel(factory = routeDependencies.rssViewModelFactory)
      val feedViewModel: FeedViewModel = viewModel(factory = routeDependencies.feedViewModelFactory)
      val summaryViewModel: SummaryViewModel = viewModel(factory = routeDependencies.summaryViewModelFactory)
      RssRoute(
        modifier = modifier,
        tab = requireNotNull(selectedTab.rssTab()),
        rssViewModel = rssViewModel,
        feedViewModel = feedViewModel,
        controller = rssController,
        onOpen = onOpenArticle,
        onSummarize = { article -> summaryViewModel.summarize(article) },
        onEditTags = bookmarkEditController::editTags,
        onMoveFolder = bookmarkEditController::moveFolder,
      )
    }

    MainTab.REDDIT_UNREAD,
    MainTab.REDDIT_READ_LATER -> {
      val redditViewModel: RedditViewModel = viewModel(factory = routeDependencies.redditViewModelFactory)
      val summaryViewModel: SummaryViewModel = viewModel(factory = routeDependencies.summaryViewModelFactory)
      RedditRoute(
        modifier = modifier,
        tab = requireNotNull(selectedTab.redditTab()),
        redditViewModel = redditViewModel,
        controller = redditController,
        onOpen = onOpenArticle,
        onSummarize = { article -> summaryViewModel.summarize(article) },
      )
    }

    MainTab.REDDIT_SUBSCRIPTIONS -> {
      val redditViewModel: RedditViewModel = viewModel(factory = routeDependencies.redditViewModelFactory)
      RedditRoute(
        modifier = modifier,
        tab = requireNotNull(selectedTab.redditTab()),
        redditViewModel = redditViewModel,
        controller = redditController,
        onOpen = onOpenArticle,
        onSummarize = {},
      )
    }

    MainTab.SAVED,
    MainTab.TAGS -> {
      val bookmarkViewModel: BookmarkViewModel = viewModel(factory = routeDependencies.bookmarkViewModelFactory)
      val summaryViewModel: SummaryViewModel = viewModel(factory = routeDependencies.summaryViewModelFactory)
      BookmarkRoute(
        modifier = modifier,
        tab = requireNotNull(selectedTab.bookmarkTab()),
        bookmarkViewModel = bookmarkViewModel,
        editController = bookmarkEditController,
        onOpen = onOpenArticle,
        onSummarize = { article -> summaryViewModel.summarize(article) },
        onMoveToLibrary = routeDependencies.libraryTransfers.moveBookmarkToLibrary,
        onImportCompleted = { appViewModel.selectTab(MainTab.SAVED) },
      )
    }

    MainTab.FOLDERS,
    MainTab.BOOKMARK_IMPORT -> {
      val bookmarkViewModel: BookmarkViewModel = viewModel(factory = routeDependencies.bookmarkViewModelFactory)
      BookmarkRoute(
        modifier = modifier,
        tab = requireNotNull(selectedTab.bookmarkTab()),
        bookmarkViewModel = bookmarkViewModel,
        editController = bookmarkEditController,
        onOpen = onOpenArticle,
        onSummarize = {},
        onMoveToLibrary = routeDependencies.libraryTransfers.moveBookmarkToLibrary,
        onImportCompleted = { appViewModel.selectTab(MainTab.SAVED) },
      )
    }

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
    MainTab.YOUTUBE -> YouTubeRouteHost(
      viewModelFactory = routeDependencies.youtubeViewModelFactory,
      modifier = modifier,
    )
    MainTab.X -> XViewerRoute(
      repository = routeDependencies.xViewerCssRepository,
      modifier = modifier,
    )
    MainTab.TASKS -> TaskRoute(
      viewModelFactory = routeDependencies.taskViewModelFactory,
      modifier = modifier,
    )
    MainTab.CALENDAR -> CalendarRoute(
      viewModelFactory = routeDependencies.calendarViewModelFactory,
      modifier = modifier,
    )
    MainTab.GAME -> GameRouteHost(
      modifier = modifier,
      onFullscreenChange = onGameFullscreenChange,
    )
    MainTab.HEALTH -> HealthRoute(
      viewModelFactory = routeDependencies.health.viewModelFactory,
      readPermissions = routeDependencies.health.readPermissions,
      modifier = modifier,
    )
    MainTab.WORKOUT -> WorkoutRoute(
      viewModelFactory = routeDependencies.workoutViewModelFactory,
      modifier = modifier,
    )
    MainTab.AI_CHAT -> {
      val chatViewModel: ChatViewModel = viewModel(factory = routeDependencies.chatViewModelFactory)
      ChatRoute(modifier = modifier, chatViewModel = chatViewModel)
    }
    MainTab.FEEDS -> {
      val feedViewModel: FeedViewModel = viewModel(factory = routeDependencies.feedViewModelFactory)
      FeedRoute(
        modifier = modifier,
        feedViewModel = feedViewModel,
        controller = rssController,
        onFeedReady = { appViewModel.selectTab(MainTab.FEEDS) },
      )
    }
    MainTab.SETTINGS -> {
      val backupViewModel: BackupViewModel = viewModel(factory = routeDependencies.backupViewModelFactory)
      val aiSettingsViewModel: AiSettingsViewModel = viewModel(factory = routeDependencies.aiSettingsViewModelFactory)
      SettingsRoute(
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
}
