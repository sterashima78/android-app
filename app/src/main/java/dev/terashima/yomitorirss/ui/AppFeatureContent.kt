package dev.terashima.yomitorirss.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.terashima.yomitorirss.AppRouteDependencies
import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.asset.AssetRoute
import dev.terashima.yomitorirss.feature.backup.BackupViewModel
import dev.terashima.yomitorirss.feature.bookmark.BookmarkEditController
import dev.terashima.yomitorirss.feature.bookmark.BookmarkRoute
import dev.terashima.yomitorirss.feature.bookmark.BookmarkViewModel
import dev.terashima.yomitorirss.feature.chat.ChatRoute
import dev.terashima.yomitorirss.feature.chat.ChatViewModel
import dev.terashima.yomitorirss.feature.game.GameRoute
import dev.terashima.yomitorirss.feature.integrated.IntegratedRoute
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeRoute
import dev.terashima.yomitorirss.feature.library.LibraryRoute
import dev.terashima.yomitorirss.feature.mail.MailRoute
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
import dev.terashima.yomitorirss.feature.x.XViewerScreen
import dev.terashima.yomitorirss.feature.youtube.YouTubeRoute

@Composable
internal fun AppFeatureContent(
  selectedTab: MainTab,
  modifier: Modifier,
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
  rssController: RssRouteController,
  redditController: RedditRouteController,
  bookmarkEditController: BookmarkEditController,
  onOpenArticle: (Article) -> Unit,
  onOpenWebServer: () -> Unit,
  onAddMailAccount: () -> Unit,
  onOpenDrawer: () -> Unit,
) {
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
    MainTab.HISTORY -> BookmarkRoute(
      modifier = modifier,
      tab = requireNotNull(selectedTab.bookmarkTab()),
      bookmarkViewModel = bookmarkViewModel,
      editController = bookmarkEditController,
      onOpen = onOpenArticle,
      onSummarize = summarize,
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
    MainTab.MAIL -> MailRoute(
      modifier = modifier,
      mailViewModel = mailViewModel,
      onAddAccount = onAddMailAccount,
    )
    MainTab.YOUTUBE -> YouTubeRoute(
      viewModelFactory = routeDependencies.youtubeViewModelFactory,
      modifier = modifier,
    )
    MainTab.X -> Box(modifier = modifier) {
      XViewerScreen(modifier = Modifier.fillMaxSize())
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
        IconButton(onClick = onOpenDrawer) {
          Icon(Icons.Default.Menu, contentDescription = "メニュー")
        }
      }
    }
    MainTab.TASKS -> TaskScreen(
      viewModelFactory = routeDependencies.taskViewModelFactory,
      onTasksChanged = routeDependencies.updateTaskWidget,
      modifier = modifier,
    )
    MainTab.GAME -> GameRoute(modifier = modifier)
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
      bookmarkViewModel = bookmarkViewModel,
      backupViewModel = backupViewModel,
      aiSettingsViewModel = aiSettingsViewModel,
      onOpenWebServer = onOpenWebServer,
      onNavigate = appViewModel::selectTab,
    )
  }
}
