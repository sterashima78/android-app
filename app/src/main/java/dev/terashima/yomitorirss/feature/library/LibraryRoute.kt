package dev.terashima.yomitorirss.feature.library

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.terashima.yomitorirss.YomitoriApplication
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.library.data.DefaultLibraryRepository
import dev.terashima.yomitorirss.feature.library.data.GoogleBooksAuthorizationManager
import dev.terashima.yomitorirss.feature.library.data.GoogleBooksAuthorizationOutcome
import kotlinx.coroutines.launch

@Composable
fun LibraryRoute(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val application = context.applicationContext as YomitoriApplication
  val authorization = remember(application) { GoogleBooksAuthorizationManager(application) }
  val repository = remember(application) {
    DefaultLibraryRepository(DatabaseConnection(application.container.database))
  }
  val libraryViewModel: LibraryViewModel = viewModel(
    factory = LibraryViewModel.Factory(repository),
  )
  val state by libraryViewModel.state.collectAsState()
  val scope = rememberCoroutineScope()

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

  LibraryScreen(
    modifier = modifier,
    state = state,
    onSyncGooglePlayBooks = requestSync,
    onHideBook = libraryViewModel::hideBook,
    onRestoreBook = libraryViewModel::restoreBook,
    onDismissMessage = libraryViewModel::dismissMessage,
  )
}
