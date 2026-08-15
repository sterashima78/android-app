package dev.terashima.yomitorirss.feature.library

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.terashima.yomitorirss.YomitoriApplication
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeRoute
import dev.terashima.yomitorirss.feature.library.data.GoogleBooksAuthorizationManager
import dev.terashima.yomitorirss.feature.library.data.GoogleBooksAuthorizationOutcome
import dev.terashima.yomitorirss.feature.library.data.SeriesAwareLibraryRepository
import kotlinx.coroutines.launch

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
  val libraryViewModel: LibraryViewModel = viewModel(
    factory = LibraryViewModel.Factory(repository),
  )
  val scope = rememberCoroutineScope()
  var section by remember { mutableStateOf(LibrarySection.BOOKS) }

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

  Column(modifier = modifier) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
      FilterChip(
        selected = section == LibrarySection.BOOKS,
        onClick = { section = LibrarySection.BOOKS },
        label = { Text("蔵書") },
        modifier = Modifier.padding(end = 8.dp),
      )
      FilterChip(
        selected = section == LibrarySection.KNOWLEDGE,
        onClick = { section = LibrarySection.KNOWLEDGE },
        label = { Text("ナレッジ") },
      )
    }

    when (section) {
      LibrarySection.BOOKS -> LibraryFeatureRoute(
        modifier = Modifier.fillMaxSize().weight(1f),
        viewModel = libraryViewModel,
        onSyncGooglePlayBooks = requestSync,
      )
      LibrarySection.KNOWLEDGE -> KnowledgeRoute(
        modifier = Modifier.fillMaxSize().weight(1f),
      )
    }
  }
}

private enum class LibrarySection {
  BOOKS,
  KNOWLEDGE,
}
