package dev.terashima.yomitorirss.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.terashima.yomitorirss.feature.backup.BackupViewModel
import dev.terashima.yomitorirss.feature.bookmark.BookmarkViewModel
import dev.terashima.yomitorirss.feature.navigation.AppViewModel
import dev.terashima.yomitorirss.feature.reddit.RedditViewModel
import dev.terashima.yomitorirss.feature.rss.FeedViewModel
import dev.terashima.yomitorirss.feature.rss.RssViewModel
import dev.terashima.yomitorirss.feature.settings.AiSettingsViewModel
import dev.terashima.yomitorirss.feature.summary.SummaryDialog
import dev.terashima.yomitorirss.feature.summary.SummaryViewModel

@Composable
internal fun FeatureMessageEffects(
  snackbarHostState: SnackbarHostState,
  appViewModel: AppViewModel,
  rssViewModel: RssViewModel,
  redditViewModel: RedditViewModel,
  feedViewModel: FeedViewModel,
  bookmarkViewModel: BookmarkViewModel,
  summaryViewModel: SummaryViewModel,
  backupViewModel: BackupViewModel,
  aiSettingsViewModel: AiSettingsViewModel,
) {
  val appState by appViewModel.state.collectAsState()
  val rssState by rssViewModel.state.collectAsState()
  val redditState by redditViewModel.state.collectAsState()
  val feedState by feedViewModel.state.collectAsState()
  val bookmarkState by bookmarkViewModel.state.collectAsState()
  val summaryState by summaryViewModel.state.collectAsState()
  val backupState by backupViewModel.state.collectAsState()
  val aiState by aiSettingsViewModel.state.collectAsState()

  LaunchedEffect(appState.message) {
    val message = appState.message ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(message)
    appViewModel.dismissMessage()
  }
  LaunchedEffect(rssState.message) {
    val message = rssState.message ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(message)
    rssViewModel.dismissMessage()
  }
  LaunchedEffect(redditState.message) {
    val message = redditState.message ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(message)
    redditViewModel.dismissMessage()
  }
  LaunchedEffect(feedState.message) {
    val message = feedState.message ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(message)
    feedViewModel.dismissMessage()
  }
  LaunchedEffect(bookmarkState.message) {
    val message = bookmarkState.message ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(message)
    bookmarkViewModel.dismissMessage()
  }
  LaunchedEffect(summaryState.message) {
    val message = summaryState.message ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(message)
    summaryViewModel.dismissMessage()
  }
  LaunchedEffect(backupState.message) {
    val message = backupState.message ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(message)
    backupViewModel.dismissMessage()
  }
  LaunchedEffect(aiState.message) {
    val message = aiState.message ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(message)
    aiSettingsViewModel.dismissMessage()
  }
}

@Composable
internal fun SummaryOverlay(
  summaryViewModel: SummaryViewModel,
  aiSettingsViewModel: AiSettingsViewModel,
) {
  val summaryState by summaryViewModel.state.collectAsState()
  val aiState by aiSettingsViewModel.state.collectAsState()
  summaryState.article?.let { article ->
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
