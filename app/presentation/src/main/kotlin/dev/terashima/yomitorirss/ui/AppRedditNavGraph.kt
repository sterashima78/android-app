package dev.terashima.yomitorirss.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.terashima.yomitorirss.AppRouteDependencies
import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.reddit.REDDIT_READ_LATER_ROUTE
import dev.terashima.yomitorirss.feature.reddit.REDDIT_SUBSCRIPTIONS_ROUTE
import dev.terashima.yomitorirss.feature.reddit.REDDIT_UNREAD_ROUTE
import dev.terashima.yomitorirss.feature.reddit.RedditRoute
import dev.terashima.yomitorirss.feature.reddit.RedditRouteController
import dev.terashima.yomitorirss.feature.reddit.RedditViewModel
import dev.terashima.yomitorirss.feature.reddit.redditTabForRoute
import dev.terashima.yomitorirss.feature.summary.SummaryViewModel

internal fun NavGraphBuilder.registerRedditDestinations(
  routeDependencies: AppRouteDependencies,
  redditController: RedditRouteController,
  onOpenArticle: (Article) -> Unit,
) {
  listOf(REDDIT_UNREAD_ROUTE, REDDIT_READ_LATER_ROUTE).forEach { route ->
    composable(route) {
      val redditViewModel: RedditViewModel = viewModel(factory = routeDependencies.redditViewModelFactory)
      val summaryViewModel: SummaryViewModel = viewModel(factory = routeDependencies.summaryViewModelFactory)
      RedditRoute(
        modifier = Modifier.fillMaxSize(),
        tab = requireNotNull(redditTabForRoute(route)),
        redditViewModel = redditViewModel,
        controller = redditController,
        onOpen = onOpenArticle,
        onSummarize = { article -> summaryViewModel.summarize(article) },
      )
    }
  }

  composable(REDDIT_SUBSCRIPTIONS_ROUTE) {
    val redditViewModel: RedditViewModel = viewModel(factory = routeDependencies.redditViewModelFactory)
    RedditRoute(
      modifier = Modifier.fillMaxSize(),
      tab = requireNotNull(redditTabForRoute(REDDIT_SUBSCRIPTIONS_ROUTE)),
      redditViewModel = redditViewModel,
      controller = redditController,
      onOpen = onOpenArticle,
      onSummarize = {},
    )
  }
}
