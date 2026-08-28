package dev.terashima.yomitorirss.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.terashima.yomitorirss.AppRouteDependencies
import dev.terashima.yomitorirss.feature.backup.BackupViewModel
import dev.terashima.yomitorirss.feature.bookmark.BookmarkViewModel
import dev.terashima.yomitorirss.feature.reddit.RedditViewModel
import dev.terashima.yomitorirss.feature.rss.FeedViewModel
import dev.terashima.yomitorirss.feature.rss.RssViewModel
import dev.terashima.yomitorirss.feature.settings.AiSettingsViewModel
import dev.terashima.yomitorirss.feature.summary.SummaryViewModel

@Composable
internal fun FeatureMessageEffects(
  selectedRoute: String,
  viewModelStoreOwner: ViewModelStoreOwner,
  snackbarHostState: SnackbarHostState,
  routeDependencies: AppRouteDependencies,
) {
  val messageSources = selectedRoute.featureMessageSources()

  if (FeatureMessageSource.RSS in messageSources) {
    val rssViewModel: RssViewModel = viewModel(
      viewModelStoreOwner = viewModelStoreOwner,
      factory = routeDependencies.rssViewModelFactory,
    )
    val rssState by rssViewModel.state.collectAsState()
    FeatureMessageEffect(rssState.message, snackbarHostState, rssViewModel::dismissMessage)
  }

  if (FeatureMessageSource.REDDIT in messageSources) {
    val redditViewModel: RedditViewModel = viewModel(
      viewModelStoreOwner = viewModelStoreOwner,
      factory = routeDependencies.redditViewModelFactory,
    )
    val redditState by redditViewModel.state.collectAsState()
    FeatureMessageEffect(redditState.message, snackbarHostState, redditViewModel::dismissMessage)
  }

  if (FeatureMessageSource.FEED in messageSources) {
    val feedViewModel: FeedViewModel = viewModel(
      viewModelStoreOwner = viewModelStoreOwner,
      factory = routeDependencies.feedViewModelFactory,
    )
    val feedState by feedViewModel.state.collectAsState()
    FeatureMessageEffect(feedState.message, snackbarHostState, feedViewModel::dismissMessage)
  }

  if (FeatureMessageSource.BOOKMARK in messageSources) {
    val bookmarkViewModel: BookmarkViewModel = viewModel(
      viewModelStoreOwner = viewModelStoreOwner,
      factory = routeDependencies.bookmarkViewModelFactory,
    )
    val bookmarkState by bookmarkViewModel.state.collectAsState()
    FeatureMessageEffect(bookmarkState.message, snackbarHostState, bookmarkViewModel::dismissMessage)
  }

  if (FeatureMessageSource.SUMMARY in messageSources) {
    val summaryViewModel: SummaryViewModel = viewModel(
      viewModelStoreOwner = viewModelStoreOwner,
      factory = routeDependencies.summaryViewModelFactory,
    )
    val summaryState by summaryViewModel.state.collectAsState()
    FeatureMessageEffect(summaryState.message, snackbarHostState, summaryViewModel::dismissMessage)
  }

  if (FeatureMessageSource.BACKUP in messageSources) {
    val backupViewModel: BackupViewModel = viewModel(
      viewModelStoreOwner = viewModelStoreOwner,
      factory = routeDependencies.backupViewModelFactory,
    )
    val backupState by backupViewModel.state.collectAsState()
    FeatureMessageEffect(backupState.message, snackbarHostState, backupViewModel::dismissMessage)
  }

  if (FeatureMessageSource.AI_SETTINGS in messageSources) {
    val aiSettingsViewModel: AiSettingsViewModel = viewModel(
      viewModelStoreOwner = viewModelStoreOwner,
      factory = routeDependencies.aiSettingsViewModelFactory,
    )
    val aiState by aiSettingsViewModel.state.collectAsState()
    FeatureMessageEffect(aiState.message, snackbarHostState, aiSettingsViewModel::dismissMessage)
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
