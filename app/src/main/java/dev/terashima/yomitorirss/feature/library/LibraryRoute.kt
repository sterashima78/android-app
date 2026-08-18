package dev.terashima.yomitorirss.feature.library

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.terashima.yomitorirss.LibraryRouteDependencies
import dev.terashima.yomitorirss.feature.library.data.GoogleBooksAuthorizationOutcome
import kotlinx.coroutines.launch

@Composable
fun LibraryRoute(
  dependencies: LibraryRouteDependencies,
  modifier: Modifier = Modifier,
) {
  val authorization = dependencies.authorization
  val smbRepository = dependencies.smbRepository
  val libraryViewModel: LibraryViewModel = viewModel(
    factory = dependencies.libraryViewModelFactory,
  )
  val organizationViewModel: LibraryOrganizationViewModel = viewModel(
    key = "library-organization",
    factory = dependencies.organizationViewModelFactory,
  )
  var openedSmbBook by remember { mutableStateOf<LibraryBook?>(null) }
  val scope = rememberCoroutineScope()
  val closeSmbBook: () -> Unit = {
    openedSmbBook = null
    libraryViewModel.refresh()
  }

  fun acceptAuthorizationResult(data: Intent) {
    runCatching { authorization.resultFromIntent(data) }
      .onSuccess { account ->
        libraryViewModel.syncGooglePlayBooks(account.accessToken, account.accountLabel)
      }
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
            is GoogleBooksAuthorizationOutcome.Authorized -> {
              val account = outcome.account
              libraryViewModel.syncGooglePlayBooks(account.accessToken, account.accountLabel)
            }

            is GoogleBooksAuthorizationOutcome.RequiresResolution -> {
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
    onOpenSmbBook = { openedSmbBook = it },
  )

  openedSmbBook?.let { book ->
    Dialog(
      onDismissRequest = closeSmbBook,
      properties = DialogProperties(
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false,
      ),
    ) {
      SmbBookReaderRoute(
        book = book,
        repository = smbRepository,
        onBack = closeSmbBook,
        modifier = Modifier.fillMaxSize(),
      )
    }
  }
}
