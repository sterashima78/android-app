package dev.terashima.yomitorirss.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import dev.terashima.yomitorirss.AppRouteDependencies
import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.bookmark.BookmarkEditController
import dev.terashima.yomitorirss.feature.integrated.ui.INTEGRATED_ROUTE
import dev.terashima.yomitorirss.feature.reddit.RedditRouteController
import dev.terashima.yomitorirss.feature.rss.RssRouteController

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
  onOpenWebContent: (String) -> Boolean,
  onOpenWebServer: () -> Unit,
  onGameFullscreenChange: (Boolean) -> Unit,
) {
  NavHost(
    navController = navController,
    startDestination = INTEGRATED_ROUTE,
    modifier = modifier,
  ) {
    registerHomeDestination(
      navController = navController,
      routeDependencies = routeDependencies,
      onOpenArticle = onOpenArticle,
    )
    registerRssDestinations(
      navController = navController,
      routeDependencies = routeDependencies,
      rssController = rssController,
      bookmarkEditController = bookmarkEditController,
      onOpenArticle = onOpenArticle,
    )
    registerRedditDestinations(
      routeDependencies = routeDependencies,
      redditController = redditController,
      onOpenArticle = onOpenArticle,
    )
    registerBookmarkDestinations(
      navController = navController,
      routeDependencies = routeDependencies,
      bookmarkEditController = bookmarkEditController,
      onOpenArticle = onOpenArticle,
    )
    registerSingleFeatureDestinations(
      navController = navController,
      routeDependencies = routeDependencies,
      biometricLockEnabled = biometricLockEnabled,
      onBiometricLockEnabledChange = onBiometricLockEnabledChange,
      onOpenWebContent = onOpenWebContent,
      onOpenWebServer = onOpenWebServer,
      onGameFullscreenChange = onGameFullscreenChange,
    )
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
