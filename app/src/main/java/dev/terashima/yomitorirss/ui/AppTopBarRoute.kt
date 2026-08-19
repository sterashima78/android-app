package dev.terashima.yomitorirss.ui

import androidx.compose.runtime.Composable
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
  val rssViewModel: RssViewModel = viewModel(factory = routeDependencies.rssViewModelFactory)
  val redditViewModel: RedditViewModel = viewModel(factory = routeDependencies.redditViewModelFactory)
  val feedViewModel: FeedViewModel = viewModel(factory = routeDependencies.feedViewModelFactory)

  AppTopBar(
    selectedTab = selectedTab,
    rssViewModel = rssViewModel,
    redditViewModel = redditViewModel,
    feedViewModel = feedViewModel,
    rssController = rssController,
    redditController = redditController,
    onOpenDrawer = onOpenDrawer,
  )
}
