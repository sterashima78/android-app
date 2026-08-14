package dev.terashima.yomitorirss.feature.library

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.terashima.yomitorirss.YomitoriApplication
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.library.data.DefaultLibraryCoverEnrichmentCoordinator
import dev.terashima.yomitorirss.feature.library.data.GoogleBooksAuthorizationManager
import dev.terashima.yomitorirss.feature.library.data.GoogleBooksAuthorizationOutcome
import dev.terashima.yomitorirss.feature.library.data.SeriesAwareLibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LibraryRoute(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val application = context.applicationContext as YomitoriApplication
  val databaseConnection = remember(application) {
    DatabaseConnection(application.container.database)
  }
  val authorization = remember(application) { GoogleBooksAuthorizationManager(application) }
  val repository = remember(databaseConnection) {
    SeriesAwareLibraryRepository(databaseConnection)
  }
  val coverCoordinator = remember(application, databaseConnection) {
    DefaultLibraryCoverEnrichmentCoordinator(application, databaseConnection)
  }
  val libraryViewModel: LibraryViewModel = viewModel(
    factory = LibraryViewModel.Factory(repository),
  )
  val scope = rememberCoroutineScope()

  fun acceptAuthorizationResult(data: Intent) {
    runCatching { authorization.resultFromIntent(data) }
      .onSuccess { account ->
        libraryViewModel.syncGooglePlayBooks(account.accessToken, account.accountLabel)
      }
      .onFailure(libraryViewModel::reportError)
  }

  fun importAmazonFile(source: LibrarySource, uri: Uri) {
    scope.launch {
      runCatching {
        withContext(Dispatchers.IO) {
          application.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
          )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
          }
        }
      }.onSuccess { displayName ->
        libraryViewModel.importAmazonLibrary(source, displayName) {
          application.contentResolver.openInputStream(uri)
            ?: error("選択したファイルを開けませんでした")
        }
      }.onFailure(libraryViewModel::reportError)
    }
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

  val kindleImportLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument(),
  ) { uri ->
    uri?.let { importAmazonFile(LibrarySource.KINDLE, it) }
  }
  val audibleImportLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument(),
  ) { uri ->
    uri?.let { importAmazonFile(LibrarySource.AUDIBLE, it) }
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

  val kindleImportMimeTypes = arrayOf(
    "application/json",
    "application/octet-stream",
  )
  val audibleImportMimeTypes = arrayOf(
    "application/json",
    "application/octet-stream",
  )

  LibraryFeatureRoute(
    modifier = modifier,
    viewModel = libraryViewModel,
    coverCoordinator = coverCoordinator,
    onSyncGooglePlayBooks = requestSync,
    onImportKindle = { kindleImportLauncher.launch(kindleImportMimeTypes) },
    onImportAudible = { audibleImportLauncher.launch(audibleImportMimeTypes) },
  )
}
