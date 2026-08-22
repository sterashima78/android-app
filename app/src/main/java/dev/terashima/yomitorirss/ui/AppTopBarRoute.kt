package dev.terashima.yomitorirss.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.terashima.yomitorirss.AppRouteDependencies
import dev.terashima.yomitorirss.feature.navigation.MainTab
import dev.terashima.yomitorirss.feature.reddit.RedditRouteController
import dev.terashima.yomitorirss.feature.reddit.RedditViewModel
import dev.terashima.yomitorirss.feature.rss.FeedViewModel
import dev.terashima.yomitorirss.feature.rss.RssRouteController
import dev.terashima.yomitorirss.feature.rss.RssViewModel

@Composable
internal fun AppTopBarRoute(
  selectedTab: MainTab,
  routeDependencies: AppRouteDependencies,
  rssController: RssRouteController,
  redditController: RedditRouteController,
  onOpenDrawer: () -> Unit,
) {
  if (!selectedTab.usesGlobalTopBar()) return

  when (selectedTab) {
    MainTab.UNREAD -> {
      val rssViewModel: RssViewModel = viewModel(factory = routeDependencies.rssViewModelFactory)
      val feedViewModel: FeedViewModel = viewModel(factory = routeDependencies.feedViewModelFactory)
      val rssState by rssViewModel.state.collectAsState()
      val feedState by feedViewModel.state.collectAsState()
      AppTopBar(
        selectedTab = selectedTab,
        refreshProgress = feedState.refreshProgress,
        hasUnread = rssState.unread.isNotEmpty(),
        onMarkAllRead = rssController::requestMarkAllRead,
        onOpenDrawer = onOpenDrawer,
      )
    }

    MainTab.READ_LATER -> {
      val feedViewModel: FeedViewModel = viewModel(factory = routeDependencies.feedViewModelFactory)
      val feedState by feedViewModel.state.collectAsState()
      AppTopBar(
        selectedTab = selectedTab,
        refreshProgress = feedState.refreshProgress,
        onOpenDrawer = onOpenDrawer,
      )
    }

    MainTab.FEEDS -> {
      val feedViewModel: FeedViewModel = viewModel(factory = routeDependencies.feedViewModelFactory)
      val feedState by feedViewModel.state.collectAsState()
      val feedOpmlImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
      ) { uri -> uri?.toString()?.let(feedViewModel::importOpml) }
      AppTopBar(
        selectedTab = selectedTab,
        refreshProgress = feedState.refreshProgress,
        onImportOpml = {
          feedOpmlImportLauncher.launch(
            arrayOf(
              "application/xml",
              "text/xml",
              "text/x-opml",
              "application/x-opml",
              "application/octet-stream",
              "text/plain",
            ),
          )
        },
        onAddFeed = rssController::requestAddFeed,
        onOpenDrawer = onOpenDrawer,
      )
    }

    MainTab.REDDIT_UNREAD -> {
      val redditViewModel: RedditViewModel = viewModel(factory = routeDependencies.redditViewModelFactory)
      val redditState by redditViewModel.state.collectAsState()
      AppTopBar(
        selectedTab = selectedTab,
        refreshProgress = redditState.refreshProgress,
        hasUnread = redditState.unread.isNotEmpty(),
        onMarkAllRead = redditController::requestMarkAllRead,
        onOpenDrawer = onOpenDrawer,
      )
    }

    MainTab.REDDIT_READ_LATER,
    MainTab.REDDIT_SUBSCRIPTIONS -> {
      val redditViewModel: RedditViewModel = viewModel(factory = routeDependencies.redditViewModelFactory)
      val redditState by redditViewModel.state.collectAsState()
      AppTopBar(
        selectedTab = selectedTab,
        refreshProgress = redditState.refreshProgress,
        onOpenDrawer = onOpenDrawer,
      )
    }

    else -> AppTopBar(
      selectedTab = selectedTab,
      onOpenDrawer = onOpenDrawer,
    )
  }
}
