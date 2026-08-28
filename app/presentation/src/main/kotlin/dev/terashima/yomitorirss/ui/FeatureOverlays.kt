package dev.terashima.yomitorirss.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.terashima.yomitorirss.AppRouteDependencies
import dev.terashima.yomitorirss.feature.bookmark.BookmarkEditController
import dev.terashima.yomitorirss.feature.bookmark.BookmarkEditHost
import dev.terashima.yomitorirss.feature.bookmark.BookmarkViewModel
import dev.terashima.yomitorirss.feature.settings.AiSettingsViewModel
import dev.terashima.yomitorirss.feature.summary.SummaryDialog
import dev.terashima.yomitorirss.feature.summary.SummaryViewModel
import dev.terashima.yomitorirss.feature.summary.summaryProgressLabel

@Composable
internal fun BookmarkEditOverlay(
  routeDependencies: AppRouteDependencies,
  controller: BookmarkEditController,
  viewModelStoreOwner: ViewModelStoreOwner,
) {
  val bookmarkViewModel: BookmarkViewModel = viewModel(
    viewModelStoreOwner = viewModelStoreOwner,
    factory = routeDependencies.bookmarkViewModelFactory,
  )
  BookmarkEditHost(
    bookmarkViewModel = bookmarkViewModel,
    controller = controller,
  )
}

@Composable
internal fun SummaryOverlay(
  routeDependencies: AppRouteDependencies,
  viewModelStoreOwner: ViewModelStoreOwner,
) {
  val summaryViewModel: SummaryViewModel = viewModel(
    viewModelStoreOwner = viewModelStoreOwner,
    factory = routeDependencies.summaryViewModelFactory,
  )
  val summaryState by summaryViewModel.state.collectAsState()
  summaryState.article?.let { article ->
    val aiSettingsViewModel: AiSettingsViewModel = viewModel(
      viewModelStoreOwner = viewModelStoreOwner,
      factory = routeDependencies.aiSettingsViewModelFactory,
    )
    val aiState by aiSettingsViewModel.state.collectAsState()
    SummaryDialog(
      article = article,
      text = summaryState.text,
      loading = summaryState.loading,
      progress = aiState.summaryProgress?.let { progress ->
        summaryProgressLabel(
          stage = progress.stage,
          modelName = progress.modelName,
        )
      },
      onDismiss = summaryViewModel::dismissSummary,
      onRetry = { replaceBookmarkTags ->
        summaryViewModel.summarize(
          article = article,
          forceRefresh = true,
          replaceBookmarkTags = replaceBookmarkTags,
        )
      },
    )
  }
}
