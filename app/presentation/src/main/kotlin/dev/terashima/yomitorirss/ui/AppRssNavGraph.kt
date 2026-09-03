package dev.terashima.yomitorirss.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dev.terashima.yomitorirss.AppRouteDependencies
import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.bookmark.BookmarkEditController
import dev.terashima.yomitorirss.feature.rss.FeedRoute
import dev.terashima.yomitorirss.feature.rss.FeedViewModel
import dev.terashima.yomitorirss.feature.rss.RSS_FEEDS_ROUTE
import dev.terashima.yomitorirss.feature.rss.RSS_READ_LATER_ROUTE
import dev.terashima.yomitorirss.feature.rss.RSS_SETTINGS_ROUTE
import dev.terashima.yomitorirss.feature.rss.RSS_UNREAD_ROUTE
import dev.terashima.yomitorirss.feature.rss.RssRoute
import dev.terashima.yomitorirss.feature.rss.RssRouteController
import dev.terashima.yomitorirss.feature.rss.RssSettingsRoute
import dev.terashima.yomitorirss.feature.rss.RssViewModel
import dev.terashima.yomitorirss.feature.rss.rssTabForRoute
import dev.terashima.yomitorirss.feature.summary.SummaryViewModel

internal fun NavGraphBuilder.registerRssDestinations(
  navController: NavHostController,
  routeDependencies: AppRouteDependencies,
  rssController: RssRouteController,
  bookmarkEditController: BookmarkEditController,
  onOpenArticle: (Article) -> Unit,
) {
  listOf(RSS_UNREAD_ROUTE, RSS_READ_LATER_ROUTE).forEach { route ->
    composable(route) {
      val rssViewModel: RssViewModel = viewModel(factory = routeDependencies.rssViewModelFactory)
      val feedViewModel: FeedViewModel = viewModel(factory = routeDependencies.feedViewModelFactory)
      val summaryViewModel: SummaryViewModel = viewModel(factory = routeDependencies.summaryViewModelFactory)
      val summaryState by summaryViewModel.state.collectAsState()
      RssRoute(
        modifier = Modifier.fillMaxSize(),
        tab = requireNotNull(rssTabForRoute(route)),
        rssViewModel = rssViewModel,
        feedViewModel = feedViewModel,
        controller = rssController,
        reviewSummaryArticleId = summaryState.review.articleId,
        reviewSummaryText = summaryState.review.text,
        reviewSummaryLoading = summaryState.review.loading,
        reviewSummaryError = summaryState.review.error,
        onOpen = onOpenArticle,
        onSummarize = { article -> summaryViewModel.summarize(article) },
        onPrepareReviewSummary = summaryViewModel::prepareReview,
        onRetryReviewSummary = summaryViewModel::retryReview,
        onEditTags = bookmarkEditController::editTags,
        onMoveFolder = bookmarkEditController::moveFolder,
      )
    }
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
}
