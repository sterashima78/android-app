package dev.terashima.yomitorirss.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dev.terashima.yomitorirss.AppRouteDependencies
import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.bookmark.BOOKMARKS_ROUTE
import dev.terashima.yomitorirss.feature.bookmark.BOOKMARK_FOLDERS_ROUTE
import dev.terashima.yomitorirss.feature.bookmark.BOOKMARK_IMPORT_ROUTE
import dev.terashima.yomitorirss.feature.bookmark.BOOKMARK_TAGS_ROUTE
import dev.terashima.yomitorirss.feature.bookmark.BookmarkEditController
import dev.terashima.yomitorirss.feature.bookmark.BookmarkRoute
import dev.terashima.yomitorirss.feature.bookmark.BookmarkViewModel
import dev.terashima.yomitorirss.feature.bookmark.bookmarkTabForRoute
import dev.terashima.yomitorirss.feature.summary.SummaryViewModel

internal fun NavGraphBuilder.registerBookmarkDestinations(
  navController: NavHostController,
  routeDependencies: AppRouteDependencies,
  bookmarkEditController: BookmarkEditController,
  onOpenArticle: (Article) -> Unit,
) {
  listOf(BOOKMARKS_ROUTE, BOOKMARK_TAGS_ROUTE).forEach { route ->
    composable(route) {
      val bookmarkViewModel: BookmarkViewModel = viewModel(factory = routeDependencies.bookmarkViewModelFactory)
      val summaryViewModel: SummaryViewModel = viewModel(factory = routeDependencies.summaryViewModelFactory)
      BookmarkRoute(
        modifier = Modifier.fillMaxSize(),
        tab = requireNotNull(bookmarkTabForRoute(route)),
        bookmarkViewModel = bookmarkViewModel,
        editController = bookmarkEditController,
        onOpen = onOpenArticle,
        onSummarize = { article -> summaryViewModel.summarize(article) },
        onReprocessEnrichment = routeDependencies.reprocessBookmarkEnrichment,
        onImportCompleted = { navController.navigateTopLevel(BOOKMARKS_ROUTE) },
      )
    }
  }

  listOf(BOOKMARK_FOLDERS_ROUTE, BOOKMARK_IMPORT_ROUTE).forEach { route ->
    composable(route) {
      val bookmarkViewModel: BookmarkViewModel = viewModel(factory = routeDependencies.bookmarkViewModelFactory)
      BookmarkRoute(
        modifier = Modifier.fillMaxSize(),
        tab = requireNotNull(bookmarkTabForRoute(route)),
        bookmarkViewModel = bookmarkViewModel,
        editController = bookmarkEditController,
        onOpen = onOpenArticle,
        onSummarize = {},
        onReprocessEnrichment = routeDependencies.reprocessBookmarkEnrichment,
        onImportCompleted = { navController.navigateTopLevel(BOOKMARKS_ROUTE) },
      )
    }
  }
}
