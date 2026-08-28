package dev.terashima.yomitorirss.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.terashima.yomitorirss.AppRouteDependencies
import dev.terashima.yomitorirss.feature.reddit.REDDIT_READ_LATER_ROUTE
import dev.terashima.yomitorirss.feature.reddit.REDDIT_SUBSCRIPTIONS_ROUTE
import dev.terashima.yomitorirss.feature.reddit.REDDIT_UNREAD_ROUTE
import dev.terashima.yomitorirss.feature.reddit.RedditRouteController
import dev.terashima.yomitorirss.feature.reddit.RedditViewModel
import dev.terashima.yomitorirss.feature.rss.RSS_FEEDS_ROUTE
import dev.terashima.yomitorirss.feature.rss.RSS_READ_LATER_ROUTE
import dev.terashima.yomitorirss.feature.rss.RSS_UNREAD_ROUTE
import dev.terashima.yomitorirss.feature.rss.FeedViewModel
import dev.terashima.yomitorirss.feature.rss.RssRouteController
import dev.terashima.yomitorirss.feature.rss.RssViewModel

@Composable
internal fun AppTopBarRoute(
  selectedRoute: String,
  viewModelStoreOwner: ViewModelStoreOwner,
  routeDependencies: AppRouteDependencies,
  rssController: RssRouteController,
  redditController: RedditRouteController,
  onOpenDrawer: () -> Unit,
) {
  if (!selectedRoute.usesGlobalTopBar()) return

  when (selectedRoute) {
    RSS_UNREAD_ROUTE -> {
      val rssViewModel: RssViewModel = viewModel(
        viewModelStoreOwner = viewModelStoreOwner,
        factory = routeDependencies.rssViewModelFactory,
      )
      val feedViewModel: FeedViewModel = viewModel(
        viewModelStoreOwner = viewModelStoreOwner,
        factory = routeDependencies.feedViewModelFactory,
      )
      val rssState by rssViewModel.state.collectAsState()
      val feedState by feedViewModel.state.collectAsState()
      AppTopBar(
        selectedRoute = selectedRoute,
        refreshProgress = feedState.refreshProgress,
        hasUnread = rssState.unread.isNotEmpty(),
        onMarkAllRead = rssController::requestMarkAllRead,
        onOpenDrawer = onOpenDrawer,
      )
    }

    RSS_READ_LATER_ROUTE -> {
      val feedViewModel: FeedViewModel = viewModel(
        viewModelStoreOwner = viewModelStoreOwner,
        factory = routeDependencies.feedViewModelFactory,
      )
      val feedState by feedViewModel.state.collectAsState()
      AppTopBar(
        selectedRoute = selectedRoute,
        refreshProgress = feedState.refreshProgress,
        onOpenDrawer = onOpenDrawer,
      )
    }

    RSS_FEEDS_ROUTE -> {
      val feedViewModel: FeedViewModel = viewModel(
        viewModelStoreOwner = viewModelStoreOwner,
        factory = routeDependencies.feedViewModelFactory,
      )
      val feedState by feedViewModel.state.collectAsState()
      val feedOpmlImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
      ) { uri -> uri?.toString()?.let(feedViewModel::importOpml) }
      AppTopBar(
        selectedRoute = selectedRoute,
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

    REDDIT_UNREAD_ROUTE -> {
      val redditViewModel: RedditViewModel = viewModel(
        viewModelStoreOwner = viewModelStoreOwner,
        factory = routeDependencies.redditViewModelFactory,
      )
      val redditState by redditViewModel.state.collectAsState()
      AppTopBar(
        selectedRoute = selectedRoute,
        refreshProgress = redditState.refreshProgress,
        hasUnread = redditState.unread.isNotEmpty(),
        onMarkAllRead = redditController::requestMarkAllRead,
        onOpenDrawer = onOpenDrawer,
      )
    }

    REDDIT_READ_LATER_ROUTE,
    REDDIT_SUBSCRIPTIONS_ROUTE -> {
      val redditViewModel: RedditViewModel = viewModel(
        viewModelStoreOwner = viewModelStoreOwner,
        factory = routeDependencies.redditViewModelFactory,
      )
      val redditState by redditViewModel.state.collectAsState()
      AppTopBar(
        selectedRoute = selectedRoute,
        refreshProgress = redditState.refreshProgress,
        onOpenDrawer = onOpenDrawer,
      )
    }

    else -> AppTopBar(
      selectedRoute = selectedRoute,
      onOpenDrawer = onOpenDrawer,
    )
  }
}
