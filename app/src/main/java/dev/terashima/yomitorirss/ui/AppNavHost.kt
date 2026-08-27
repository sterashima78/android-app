package dev.terashima.yomitorirss.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.fillMaxSize
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import dev.terashima.yomitorirss.AppRouteDependencies
import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.asset.ASSET_ROUTE
import dev.terashima.yomitorirss.feature.asset.AssetRoute
import dev.terashima.yomitorirss.feature.backup.BackupViewModel
import dev.terashima.yomitorirss.feature.bookmark.BOOKMARKS_ROUTE
import dev.terashima.yomitorirss.feature.bookmark.BOOKMARK_FOLDERS_ROUTE
import dev.terashima.yomitorirss.feature.bookmark.BOOKMARK_IMPORT_ROUTE
import dev.terashima.yomitorirss.feature.bookmark.BOOKMARK_TAGS_ROUTE
import dev.terashima.yomitorirss.feature.bookmark.BookmarkEditController
import dev.terashima.yomitorirss.feature.bookmark.BookmarkRoute
import dev.terashima.yomitorirss.feature.bookmark.BookmarkViewModel
import dev.terashima.yomitorirss.feature.calendar.CALENDAR_ROUTE
import dev.terashima.yomitorirss.feature.chat.CHAT_ROUTE
import dev.terashima.yomitorirss.feature.chat.ChatRoute
import dev.terashima.yomitorirss.feature.chat.ChatViewModel
import dev.terashima.yomitorirss.feature.game.GAME_ROUTE
import dev.terashima.yomitorirss.feature.health.HEALTH_ROUTE
import dev.terashima.yomitorirss.feature.health.HealthRoute
import dev.terashima.yomitorirss.feature.integrated.ui.INTEGRATED_ROUTE
import dev.terashima.yomitorirss.feature.integrated.ui.IntegratedRoute
import dev.terashima.yomitorirss.feature.knowledge.KNOWLEDGE_ROUTE
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeRoute
import dev.terashima.yomitorirss.feature.library.LIBRARY_ROUTE
import dev.terashima.yomitorirss.feature.mail.MAIL_ROUTE
import dev.terashima.yomitorirss.feature.mail.MailViewModel
import dev.terashima.yomitorirss.feature.reddit.REDDIT_READ_LATER_ROUTE
import dev.terashima.yomitorirss.feature.reddit.REDDIT_SUBSCRIPTIONS_ROUTE
import dev.terashima.yomitorirss.feature.reddit.REDDIT_UNREAD_ROUTE
import dev.terashima.yomitorirss.feature.reddit.RedditRoute
import dev.terashima.yomitorirss.feature.reddit.RedditRouteController
import dev.terashima.yomitorirss.feature.reddit.RedditViewModel
import dev.terashima.yomitorirss.feature.rss.RSS_FEEDS_ROUTE
import dev.terashima.yomitorirss.feature.rss.RSS_READ_LATER_ROUTE
import dev.terashima.yomitorirss.feature.rss.RSS_SETTINGS_ROUTE
import dev.terashima.yomitorirss.feature.rss.RSS_UNREAD_ROUTE
import dev.terashima.yomitorirss.feature.rss.FeedRoute
import dev.terashima.yomitorirss.feature.rss.FeedViewModel
import dev.terashima.yomitorirss.feature.rss.RssRoute
import dev.terashima.yomitorirss.feature.rss.RssRouteController
import dev.terashima.yomitorirss.feature.rss.RssSettingsRoute
import dev.terashima.yomitorirss.feature.rss.RssViewModel
import dev.terashima.yomitorirss.feature.settings.SETTINGS_ROUTE
import dev.terashima.yomitorirss.feature.settings.AiSettingsViewModel
import dev.terashima.yomitorirss.feature.summary.SummaryViewModel
import dev.terashima.yomitorirss.feature.task.TASKS_ROUTE
import dev.terashima.yomitorirss.feature.task.TaskRoute
import dev.terashima.yomitorirss.feature.workout.WORKOUT_ROUTE
import dev.terashima.yomitorirss.feature.workout.WorkoutRoute
import dev.terashima.yomitorirss.feature.x.X_ROUTE
import dev.terashima.yomitorirss.feature.x.XViewerRoute
import dev.terashima.yomitorirss.feature.youtube.YOUTUBE_ROUTE

@Composable
internal fun AppNavHost(
  navController: NavHostController,
  modifier: Modifier,
  routeDependencies: AppRouteDependencies,
  rssController: RssRouteController,
  redditController: RedditRouteController,
  bookmarkEditController: BookmarkEditController,
  biometricLockEnabled: Boolean,
  onBiometricLockEnabledChange: (Boolean) -> Unit,
  onOpenArticle: (Article) -> Unit,
  onOpenWebServer: () -> Unit,
  onGameFullscreenChange: (Boolean) -> Unit,
) {
  NavHost(
    navController = navController,
    startDestination = INTEGRATED_ROUTE,
    modifier = modifier,
  ) {
    composable(INTEGRATED_ROUTE) {
      val context = LocalContext.current
      val rssViewModel: RssViewModel = viewModel(factory = routeDependencies.rssViewModelFactory)
      val redditViewModel: RedditViewModel = viewModel(factory = routeDependencies.redditViewModelFactory)
      val feedViewModel: FeedViewModel = viewModel(factory = routeDependencies.feedViewModelFactory)
      val mailViewModel: MailViewModel = viewModel(factory = routeDependencies.mailViewModelFactory)
      val summaryViewModel: SummaryViewModel = viewModel(factory = routeDependencies.summaryViewModelFactory)
      IntegratedRoute(
        modifier = Modifier.fillMaxSize(),
        rssViewModel = rssViewModel,
        redditViewModel = redditViewModel,
        feedViewModel = feedViewModel,
        mailViewModel = mailViewModel,
        youtubeViewModelFactory = routeDependencies.youtubeViewModelFactory,
        onOpenArticle = onOpenArticle,
        onSummarize = { article -> summaryViewModel.summarize(article) },
        onNavigateToMail = { navController.navigateTopLevel(MAIL_ROUTE) },
        onOpenExternalUrl = { url ->
          runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
          }
        },
      )
    }

    composable(RSS_UNREAD_ROUTE) {
      val rssViewModel: RssViewModel = viewModel(factory = routeDependencies.rssViewModelFactory)
      val feedViewModel: FeedViewModel = viewModel(factory = routeDependencies.feedViewModelFactory)
      val summaryViewModel: SummaryViewModel = viewModel(factory = routeDependencies.summaryViewModelFactory)
      RssRoute(
        modifier = Modifier.fillMaxSize(),
        tab = requireNotNull(RSS_UNREAD_ROUTE.rssTab()),
        rssViewModel = rssViewModel,
        feedViewModel = feedViewModel,
        controller = rssController,
        onOpen = onOpenArticle,
        onSummarize = { article -> summaryViewModel.summarize(article) },
        onEditTags = bookmarkEditController::editTags,
        onMoveFolder = bookmarkEditController::moveFolder,
      )
    }

    composable(RSS_READ_LATER_ROUTE) {
      val rssViewModel: RssViewModel = viewModel(factory = routeDependencies.rssViewModelFactory)
      val feedViewModel: FeedViewModel = viewModel(factory = routeDependencies.feedViewModelFactory)
      val summaryViewModel: SummaryViewModel = viewModel(factory = routeDependencies.summaryViewModelFactory)
      RssRoute(
        modifier = Modifier.fillMaxSize(),
        tab = requireNotNull(RSS_READ_LATER_ROUTE.rssTab()),
        rssViewModel = rssViewModel,
        feedViewModel = feedViewModel,
        controller = rssController,
        onOpen = onOpenArticle,
        onSummarize = { article -> summaryViewModel.summarize(article) },
        onEditTags = bookmarkEditController::editTags,
        onMoveFolder = bookmarkEditController::moveFolder,
      )
    }

    composable(REDDIT_UNREAD_ROUTE) {
      val redditViewModel: RedditViewModel = viewModel(factory = routeDependencies.redditViewModelFactory)
      val summaryViewModel: SummaryViewModel = viewModel(factory = routeDependencies.summaryViewModelFactory)
      RedditRoute(
        modifier = Modifier.fillMaxSize(),
        tab = requireNotNull(REDDIT_UNREAD_ROUTE.redditTab()),
        redditViewModel = redditViewModel,
        controller = redditController,
        onOpen = onOpenArticle,
        onSummarize = { article -> summaryViewModel.summarize(article) },
      )
    }

    composable(REDDIT_READ_LATER_ROUTE) {
      val redditViewModel: RedditViewModel = viewModel(factory = routeDependencies.redditViewModelFactory)
      val summaryViewModel: SummaryViewModel = viewModel(factory = routeDependencies.summaryViewModelFactory)
      RedditRoute(
        modifier = Modifier.fillMaxSize(),
        tab = requireNotNull(REDDIT_READ_LATER_ROUTE.redditTab()),
        redditViewModel = redditViewModel,
        controller = redditController,
        onOpen = onOpenArticle,
        onSummarize = { article -> summaryViewModel.summarize(article) },
      )
    }

    composable(REDDIT_SUBSCRIPTIONS_ROUTE) {
      val redditViewModel: RedditViewModel = viewModel(factory = routeDependencies.redditViewModelFactory)
      RedditRoute(
        modifier = Modifier.fillMaxSize(),
        tab = requireNotNull(REDDIT_SUBSCRIPTIONS_ROUTE.redditTab()),
        redditViewModel = redditViewModel,
        controller = redditController,
        onOpen = onOpenArticle,
        onSummarize = {},
      )
    }

    composable(BOOKMARKS_ROUTE) {
      val bookmarkViewModel: BookmarkViewModel = viewModel(factory = routeDependencies.bookmarkViewModelFactory)
      val summaryViewModel: SummaryViewModel = viewModel(factory = routeDependencies.summaryViewModelFactory)
      BookmarkRoute(
        modifier = Modifier.fillMaxSize(),
        tab = requireNotNull(BOOKMARKS_ROUTE.bookmarkTab()),
        bookmarkViewModel = bookmarkViewModel,
        editController = bookmarkEditController,
        onOpen = onOpenArticle,
        onSummarize = { article -> summaryViewModel.summarize(article) },
        onReprocessEnrichment = routeDependencies.reprocessBookmarkEnrichment,
        onImportCompleted = { navController.navigateTopLevel(BOOKMARKS_ROUTE) },
      )
    }

    composable(BOOKMARK_TAGS_ROUTE) {
      val bookmarkViewModel: BookmarkViewModel = viewModel(factory = routeDependencies.bookmarkViewModelFactory)
      val summaryViewModel: SummaryViewModel = viewModel(factory = routeDependencies.summaryViewModelFactory)
      BookmarkRoute(
        modifier = Modifier.fillMaxSize(),
        tab = requireNotNull(BOOKMARK_TAGS_ROUTE.bookmarkTab()),
        bookmarkViewModel = bookmarkViewModel,
        editController = bookmarkEditController,
        onOpen = onOpenArticle,
        onSummarize = { article -> summaryViewModel.summarize(article) },
        onReprocessEnrichment = routeDependencies.reprocessBookmarkEnrichment,
        onImportCompleted = { navController.navigateTopLevel(BOOKMARKS_ROUTE) },
      )
    }

    composable(BOOKMARK_FOLDERS_ROUTE) {
      val bookmarkViewModel: BookmarkViewModel = viewModel(factory = routeDependencies.bookmarkViewModelFactory)
      BookmarkRoute(
        modifier = Modifier.fillMaxSize(),
        tab = requireNotNull(BOOKMARK_FOLDERS_ROUTE.bookmarkTab()),
        bookmarkViewModel = bookmarkViewModel,
        editController = bookmarkEditController,
        onOpen = onOpenArticle,
        onSummarize = {},
        onReprocessEnrichment = routeDependencies.reprocessBookmarkEnrichment,
        onImportCompleted = { navController.navigateTopLevel(BOOKMARKS_ROUTE) },
      )
    }

    composable(BOOKMARK_IMPORT_ROUTE) {
      val bookmarkViewModel: BookmarkViewModel = viewModel(factory = routeDependencies.bookmarkViewModelFactory)
      BookmarkRoute(
        modifier = Modifier.fillMaxSize(),
        tab = requireNotNull(BOOKMARK_IMPORT_ROUTE.bookmarkTab()),
        bookmarkViewModel = bookmarkViewModel,
        editController = bookmarkEditController,
        onOpen = onOpenArticle,
        onSummarize = {},
        onReprocessEnrichment = routeDependencies.reprocessBookmarkEnrichment,
        onImportCompleted = { navController.navigateTopLevel(BOOKMARKS_ROUTE) },
      )
    }

    composable(LIBRARY_ROUTE) {
      LibraryRoute(
        dependencies = routeDependencies.library,
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
    composable(RSS_FEEDS_ROUTE) {
      val feedViewModel: FeedViewModel = viewModel(factory = routeDependencies.feedViewModelFactory)
      FeedRoute(
        modifier = Modifier.fillMaxSize(),
        feedViewModel = feedViewModel,
        controller = rssController,
        onFeedReady = { navController.navigateTopLevel(RSS_FEEDS_ROUTE) },
      )
    }
    composable(RSS_SETTINGS_ROUTE) {
      val feedViewModel: FeedViewModel = viewModel(factory = routeDependencies.feedViewModelFactory)
      RssSettingsRoute(
        modifier = Modifier.fillMaxSize(),
        feedViewModel = feedViewModel,
      )
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
        onNavigate = navController::navigateTopLevel,
      )
    }
  }
}

internal fun NavHostController.navigateTopLevel(route: String) {
  require(route in allAppRoutes) { "Unknown app route: $route" }
  if (currentDestination?.route == route) return

  navigate(route) {
    launchSingleTop = true
    restoreState = true
    popUpTo(graph.startDestinationId) {
      saveState = true
    }
  }
}
