package dev.terashima.yomitorirss.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.terashima.yomitorirss.LibraryAuthorizationOutcome
import dev.terashima.yomitorirss.LibraryRouteDependencies
import dev.terashima.yomitorirss.feature.library.LibraryFeatureRoute
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationViewModel
import dev.terashima.yomitorirss.feature.library.LibraryViewModel
import kotlinx.coroutines.launch

@Composable
internal fun LibraryRoute(
  dependencies: LibraryRouteDependencies,
  modifier: Modifier = Modifier,
) {
  val authorization = dependencies.authorization
  val libraryViewModel: LibraryViewModel = viewModel(factory = dependencies.libraryViewModelFactory)
  val organizationViewModel: LibraryOrganizationViewModel = viewModel(
    key = "library-organization",
    factory = dependencies.organizationViewModelFactory,
  )
  val scope = rememberCoroutineScope()

  fun acceptAuthorizationResult(data: Intent) {
    runCatching { authorization.resultFromIntent(data) }
      .onSuccess { account -> libraryViewModel.syncGooglePlayBooks(account.accessToken, account.accountLabel) }
      .onFailure(libraryViewModel::reportError)
  }

  val authorizationLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartIntentSenderForResult(),
  ) { result ->
    val data = result.data
    if (data == null) {
      libraryViewModel.reportError(IllegalStateException("Google Books の認証結果を取得できませんでした"))
    } else {
      acceptAuthorizationResult(data)
    }
  }

  val requestSync: () -> Unit = {
    scope.launch {
      runCatching { authorization.requestAccount() }
        .onSuccess { outcome ->
          when (outcome) {
            is LibraryAuthorizationOutcome.Authorized -> {
              val account = outcome.account
              libraryViewModel.syncGooglePlayBooks(account.accessToken, account.accountLabel)
            }
            is LibraryAuthorizationOutcome.RequiresResolution -> {
              authorizationLauncher.launch(
                IntentSenderRequest.Builder(outcome.pendingIntent.intentSender).build(),
              )
            }
          }
        }
        .onFailure(libraryViewModel::reportError)
    }
  }

  LibraryFeatureRoute(
    modifier = modifier,
    viewModel = libraryViewModel,
    organizationViewModel = organizationViewModel,
    onSyncGooglePlayBooks = requestSync,
    onAddWebBook = { url -> dependencies.addWebBook(url, null) },
    onMoveWebBookToBookmark = dependencies.moveWebBookToBookmark,
    smbRepository = dependencies.smbRepository,
    pageSourceFactory = dependencies.bookReader.pageSourceFactory,
    readingPositionStore = dependencies.bookReader.readingPositionStore,
  )
}
