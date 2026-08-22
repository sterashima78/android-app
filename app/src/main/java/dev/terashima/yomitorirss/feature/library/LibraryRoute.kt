package dev.terashima.yomitorirss.feature.library

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.terashima.yomitorirss.LibraryAuthorizationOutcome
import dev.terashima.yomitorirss.LibraryRouteDependencies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
  val state by libraryViewModel.state.collectAsState()
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

  Box(modifier = modifier.fillMaxSize()) {
    LibraryFeatureRoute(
      modifier = Modifier.fillMaxSize(),
      viewModel = libraryViewModel,
      organizationViewModel = organizationViewModel,
      onSyncGooglePlayBooks = requestSync,
      onOpenSmbBook = { openedSmbBook = it },
    )

    WebLibraryActions(
      books = state.books.filter { it.source == LibrarySource.WEB },
      onAdd = { url ->
        scope.launch {
          runCatching {
            withContext(Dispatchers.IO) {
              dependencies.webLibraryMutator.addWebBook(url)
            }
          }
            .onSuccess { libraryViewModel.refresh() }
            .onFailure(libraryViewModel::reportError)
        }
      },
      onMoveToBookmark = { book ->
        scope.launch {
          runCatching {
            withContext(Dispatchers.IO) {
              dependencies.moveWebBookToBookmark(book)
            }
          }
            .onSuccess { libraryViewModel.refresh() }
            .onFailure(libraryViewModel::reportError)
        }
      },
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(end = 16.dp, bottom = 88.dp),
    )
  }

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
        dependencies = dependencies.bookReader,
        onBack = closeSmbBook,
        modifier = Modifier.fillMaxSize(),
      )
    }
  }
}
