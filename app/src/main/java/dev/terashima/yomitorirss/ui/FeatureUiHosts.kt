package dev.terashima.yomitorirss.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.terashima.yomitorirss.AppRouteDependencies
import dev.terashima.yomitorirss.feature.backup.BackupViewModel
import dev.terashima.yomitorirss.feature.bookmark.BookmarkEditController
import dev.terashima.yomitorirss.feature.bookmark.BookmarkEditHost
import dev.terashima.yomitorirss.feature.bookmark.BookmarkViewModel
import dev.terashima.yomitorirss.feature.navigation.AppViewModel
import dev.terashima.yomitorirss.feature.navigation.MainTab
import dev.terashima.yomitorirss.feature.reddit.RedditViewModel
import dev.terashima.yomitorirss.feature.rss.FeedViewModel
import dev.terashima.yomitorirss.feature.rss.RssViewModel
import dev.terashima.yomitorirss.feature.settings.AiSettingsViewModel
import dev.terashima.yomitorirss.feature.summary.SummaryDialog
import dev.terashima.yomitorirss.feature.summary.SummaryViewModel

@Composable
internal fun FeatureMessageEffects(
  selectedTab: MainTab,
  snackbarHostState: SnackbarHostState,
  appViewModel: AppViewModel,
  routeDependencies: AppRouteDependencies,
) {
  val appState by appViewModel.state.collectAsState()
  FeatureMessageEffect(
    message = appState.message,
    snackbarHostState = snackbarHostState,
    onConsumed = appViewModel::dismissMessage,
  )

  when (selectedTab) {
    MainTab.INTEGRATED -> {
      val rssViewModel: RssViewModel = viewModel(factory = routeDependencies.rssViewModelFactory)
      val redditViewModel: RedditViewModel = viewModel(factory = routeDependencies.redditViewModelFactory)
      val feedViewModel: FeedViewModel = viewModel(factory = routeDependencies.feedViewModelFactory)
      val summaryViewModel: SummaryViewModel = viewModel(factory = routeDependencies.summaryViewModelFactory)
      val rssState by rssViewModel.state.collectAsState()
      val redditState by redditViewModel.state.collectAsState()
      val feedState by feedViewModel.state.collectAsState()
      val summaryState by summaryViewModel.state.collectAsState()
      FeatureMessageEffect(rssState.message, snackbarHostState, rssViewModel::dismissMessage)
      FeatureMessageEffect(redditState.message, snackbarHostState, redditViewModel::dismissMessage)
      FeatureMessageEffect(feedState.message, snackbarHostState, feedViewModel::dismissMessage)
      FeatureMessageEffect(summaryState.message, snackbarHostState, summaryViewModel::dismissMessage)
    }

    MainTab.UNREAD,
    MainTab.READ_LATER -> {
      val rssViewModel: RssViewModel = viewModel(factory = routeDependencies.rssViewModelFactory)
      val summaryViewModel: SummaryViewModel = viewModel(factory = routeDependencies.summaryViewModelFactory)
      val rssState by rssViewModel.state.collectAsState()
      val summaryState by summaryViewModel.state.collectAsState()
      FeatureMessageEffect(rssState.message, snackbarHostState, rssViewModel::dismissMessage)
      FeatureMessageEffect(summaryState.message, snackbarHostState, summaryViewModel::dismissMessage)
    }

    MainTab.FEEDS -> {
      val feedViewModel: FeedViewModel = viewModel(factory = routeDependencies.feedViewModelFactory)
      val feedState by feedViewModel.state.collectAsState()
      FeatureMessageEffect(feedState.message, snackbarHostState, feedViewModel::dismissMessage)
    }

    MainTab.REDDIT_UNREAD,
    MainTab.REDDIT_READ_LATER -> {
      val redditViewModel: RedditViewModel = viewModel(factory = routeDependencies.redditViewModelFactory)
      val summaryViewModel: SummaryViewModel = viewModel(factory = routeDependencies.summaryViewModelFactory)
      val redditState by redditViewModel.state.collectAsState()
      val summaryState by summaryViewModel.state.collectAsState()
      FeatureMessageEffect(redditState.message, snackbarHostState, redditViewModel::dismissMessage)
      FeatureMessageEffect(summaryState.message, snackbarHostState, summaryViewModel::dismissMessage)
    }

    MainTab.REDDIT_SUBSCRIPTIONS -> {
      val redditViewModel: RedditViewModel = viewModel(factory = routeDependencies.redditViewModelFactory)
      val redditState by redditViewModel.state.collectAsState()
      FeatureMessageEffect(redditState.message, snackbarHostState, redditViewModel::dismissMessage)
    }

    MainTab.SAVED,
    MainTab.TAGS -> {
      val bookmarkViewModel: BookmarkViewModel = viewModel(factory = routeDependencies.bookmarkViewModelFactory)
      val summaryViewModel: SummaryViewModel = viewModel(factory = routeDependencies.summaryViewModelFactory)
      val bookmarkState by bookmarkViewModel.state.collectAsState()
      val summaryState by summaryViewModel.state.collectAsState()
      FeatureMessageEffect(bookmarkState.message, snackbarHostState, bookmarkViewModel::dismissMessage)
      FeatureMessageEffect(summaryState.message, snackbarHostState, summaryViewModel::dismissMessage)
    }

    MainTab.FOLDERS,
    MainTab.BOOKMARK_IMPORT -> {
      val bookmarkViewModel: BookmarkViewModel = viewModel(factory = routeDependencies.bookmarkViewModelFactory)
      val bookmarkState by bookmarkViewModel.state.collectAsState()
      FeatureMessageEffect(bookmarkState.message, snackbarHostState, bookmarkViewModel::dismissMessage)
    }

    MainTab.SETTINGS -> {
      val backupViewModel: BackupViewModel = viewModel(factory = routeDependencies.backupViewModelFactory)
      val aiSettingsViewModel: AiSettingsViewModel = viewModel(factory = routeDependencies.aiSettingsViewModelFactory)
      val backupState by backupViewModel.state.collectAsState()
      val aiState by aiSettingsViewModel.state.collectAsState()
      FeatureMessageEffect(backupState.message, snackbarHostState, backupViewModel::dismissMessage)
      FeatureMessageEffect(aiState.message, snackbarHostState, aiSettingsViewModel::dismissMessage)
    }

    else -> Unit
  }
}

@Composable
private fun FeatureMessageEffect(
  message: String?,
  snackbarHostState: SnackbarHostState,
  onConsumed: () -> Unit,
) {
  LaunchedEffect(message) {
    val current = message ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(current)
    onConsumed()
  }
}

@Composable
internal fun BookmarkEditOverlay(
  routeDependencies: AppRouteDependencies,
  controller: BookmarkEditController,
) {
  val bookmarkViewModel: BookmarkViewModel = viewModel(factory = routeDependencies.bookmarkViewModelFactory)
  BookmarkEditHost(
    bookmarkViewModel = bookmarkViewModel,
    controller = controller,
  )
}

@Composable
internal fun SummaryOverlay(routeDependencies: AppRouteDependencies) {
  val summaryViewModel: SummaryViewModel = viewModel(factory = routeDependencies.summaryViewModelFactory)
  val summaryState by summaryViewModel.state.collectAsState()
  summaryState.article?.let { article ->
    val aiSettingsViewModel: AiSettingsViewModel = viewModel(factory = routeDependencies.aiSettingsViewModelFactory)
    val aiState by aiSettingsViewModel.state.collectAsState()
    SummaryDialog(
      article = article,
      text = summaryState.text,
      loading = summaryState.loading,
      progress = aiState.summaryProgress?.let(::summaryProgressLabel),
      onDismiss = summaryViewModel::dismissSummary,
      onRetry = { summaryViewModel.summarize(article, forceRefresh = true) },
    )
  }
}
