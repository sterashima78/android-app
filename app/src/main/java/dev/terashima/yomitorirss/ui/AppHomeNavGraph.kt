package dev.terashima.yomitorirss.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dev.terashima.yomitorirss.AppRouteDependencies
import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.integrated.ui.INTEGRATED_ROUTE
import dev.terashima.yomitorirss.feature.integrated.ui.IntegratedRoute
import dev.terashima.yomitorirss.feature.mail.MAIL_ROUTE
import dev.terashima.yomitorirss.feature.mail.MailViewModel
import dev.terashima.yomitorirss.feature.reddit.RedditViewModel
import dev.terashima.yomitorirss.feature.rss.FeedViewModel
import dev.terashima.yomitorirss.feature.rss.RssViewModel
import dev.terashima.yomitorirss.feature.summary.SummaryViewModel

internal fun NavGraphBuilder.registerHomeDestination(
  navController: NavHostController,
  routeDependencies: AppRouteDependencies,
  onOpenArticle: (Article) -> Unit,
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
}
