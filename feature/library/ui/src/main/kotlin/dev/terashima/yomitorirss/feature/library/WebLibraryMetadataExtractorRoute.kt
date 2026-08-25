package dev.terashima.yomitorirss.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import dev.terashima.yomitorirss.feature.bookreader.BookPageSourceFactory
import dev.terashima.yomitorirss.feature.bookreader.ReadingPositionStore

internal data class WebLibraryMetadataExtractorUiBinding(
  val list: () -> List<WebLibraryMetadataExtractor>,
  val save: (String?, String, String) -> WebLibraryMetadataExtractor,
  val delete: (String) -> Unit,
  val onError: (Throwable) -> Unit,
)

internal val LocalWebLibraryMetadataExtractorUiBinding =
  staticCompositionLocalOf<WebLibraryMetadataExtractorUiBinding?> { null }

@Composable
fun LibraryFeatureRoute(
  viewModel: LibraryViewModel,
  organizationViewModel: LibraryOrganizationViewModel,
  onSyncGooglePlayBooks: () -> Unit,
  onAddWebBook: suspend (String) -> Unit,
  onRefreshWebBook: suspend (LibraryBook) -> Unit,
  onMoveWebBookToBookmark: suspend (LibraryBook) -> Unit,
  onDeleteWebBook: suspend (LibraryBook) -> Unit,
  onListWebMetadataExtractors: () -> List<WebLibraryMetadataExtractor>,
  onSaveWebMetadataExtractor: (String?, String, String) -> WebLibraryMetadataExtractor,
  onDeleteWebMetadataExtractor: (String) -> Unit,
  smbRepository: SmbLibraryRepository,
  pageSourceFactory: BookPageSourceFactory,
  readingPositionStore: ReadingPositionStore,
  modifier: Modifier = Modifier,
) {
  val binding = WebLibraryMetadataExtractorUiBinding(
    list = onListWebMetadataExtractors,
    save = onSaveWebMetadataExtractor,
    delete = onDeleteWebMetadataExtractor,
    onError = viewModel::reportError,
  )
  CompositionLocalProvider(LocalWebLibraryMetadataExtractorUiBinding provides binding) {
    LibraryFeatureRoute(
      viewModel = viewModel,
      organizationViewModel = organizationViewModel,
      onSyncGooglePlayBooks = onSyncGooglePlayBooks,
      onAddWebBook = onAddWebBook,
      onRefreshWebBook = onRefreshWebBook,
      onMoveWebBookToBookmark = onMoveWebBookToBookmark,
      onDeleteWebBook = onDeleteWebBook,
      smbRepository = smbRepository,
      pageSourceFactory = pageSourceFactory,
      readingPositionStore = readingPositionStore,
      modifier = modifier,
    )
  }
}
