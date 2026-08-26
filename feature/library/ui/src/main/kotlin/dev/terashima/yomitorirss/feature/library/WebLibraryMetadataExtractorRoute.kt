package dev.terashima.yomitorirss.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import dev.terashima.yomitorirss.feature.bookreader.BookPageSourceFactory
import dev.terashima.yomitorirss.feature.bookreader.ReadingPositionStore

internal data class WebLibraryMetadataExtractorUiBinding(
  val list: () -> List<WebLibraryMetadataExtractor>,
  val save: (String?, String, String, Int) -> WebLibraryMetadataExtractor,
  val delete: (String) -> Unit,
  val test: suspend (String, String, String, Int) -> WebLibraryMetadataExtractorTestResult,
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
  onRefreshWebBook: suspend (LibraryBook) -> WebLibraryMetadataRefreshResult,
  onDeleteWebBook: suspend (LibraryBook) -> Unit,
  onListWebMetadataExtractors: () -> List<WebLibraryMetadataExtractor>,
  onSaveWebMetadataExtractor: (String?, String, String, Int) -> WebLibraryMetadataExtractor,
  onDeleteWebMetadataExtractor: (String) -> Unit,
  onTestWebMetadataExtractor: suspend (String, String, String, Int) -> WebLibraryMetadataExtractorTestResult,
  onOpenWebUrl: (String) -> Unit,
  smbRepository: SmbLibraryRepository,
  pageSourceFactory: BookPageSourceFactory,
  readingPositionStore: ReadingPositionStore,
  modifier: Modifier = Modifier,
) {
  val binding = WebLibraryMetadataExtractorUiBinding(
    list = onListWebMetadataExtractors,
    save = onSaveWebMetadataExtractor,
    delete = onDeleteWebMetadataExtractor,
    test = onTestWebMetadataExtractor,
    onError = viewModel::reportError,
  )
  CompositionLocalProvider(LocalWebLibraryMetadataExtractorUiBinding provides binding) {
    LibraryFeatureRoute(
      viewModel = viewModel,
      organizationViewModel = organizationViewModel,
      onSyncGooglePlayBooks = onSyncGooglePlayBooks,
      onAddWebBook = onAddWebBook,
      onRefreshWebBook = onRefreshWebBook,
      onDeleteWebBook = onDeleteWebBook,
      onOpenWebUrl = onOpenWebUrl,
      smbRepository = smbRepository,
      pageSourceFactory = pageSourceFactory,
      readingPositionStore = readingPositionStore,
      modifier = modifier,
    )
  }
}
