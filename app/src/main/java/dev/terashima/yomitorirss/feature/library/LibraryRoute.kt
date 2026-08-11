package dev.terashima.yomitorirss.feature.library

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.terashima.yomitorirss.YomitoriApplication
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.library.data.DefaultLibraryRepository
import dev.terashima.yomitorirss.feature.library.data.GoogleBooksAuthorizationManager
import dev.terashima.yomitorirss.feature.library.data.GoogleBooksAuthorizationOutcome
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
  val libraryUriHandler = remember(context) { LibraryUriHandler(context) }

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
          val displayName = context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
          )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
          }
          val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readUpTo(MAX_AMAZON_IMPORT_BYTES + 1)
          } ?: error("選択したファイルを開けませんでした")
          require(bytes.size <= MAX_AMAZON_IMPORT_BYTES) {
            "インポートファイルが大きすぎます（上限 25 MB）"
          }
          displayName to bytes
        }
      }.onSuccess { (displayName, bytes) ->
        libraryViewModel.importAmazonLibrary(source, displayName, bytes)
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

  val importMimeTypes = arrayOf(
    "text/csv",
    "text/tab-separated-values",
    "text/plain",
    "application/zip",
    "application/octet-stream",
  )

  CompositionLocalProvider(LocalUriHandler provides libraryUriHandler) {
    LibraryScreen(
      modifier = modifier,
      state = state,
      onSyncGooglePlayBooks = requestSync,
      onImportKindle = { kindleImportLauncher.launch(importMimeTypes) },
      onImportAudible = { audibleImportLauncher.launch(importMimeTypes) },
      onHideBook = libraryViewModel::hideBook,
      onRestoreBook = libraryViewModel::restoreBook,
      onSetBookSeries = libraryViewModel::setBookSeries,
      onClearBookSeries = libraryViewModel::clearBookSeries,
      onDismissMessage = libraryViewModel::dismissMessage,
    )
  }
}

private class LibraryUriHandler(
  private val context: Context,
) : UriHandler {
  override fun openUri(uri: String) {
    val parsedUri = Uri.parse(uri)
    if (parsedUri.isGoogleBooksUri()) {
      openGooglePlayBooks(parsedUri)
    } else {
      context.startActivity(Intent(Intent.ACTION_VIEW, parsedUri))
    }
  }

  private fun openGooglePlayBooks(uri: Uri) {
    val readerUri = Uri.parse(googlePlayBooksReaderUrl(uri.toString()) ?: uri.toString())
    val explicitReaderIntent = Intent(Intent.ACTION_VIEW, readerUri).apply {
      component = ComponentName(PLAY_BOOKS_PACKAGE, PLAY_BOOKS_READER_ACTIVITY)
    }
    if (startActivity(explicitReaderIntent)) return

    val packageReaderIntent = Intent(Intent.ACTION_VIEW, readerUri).apply {
      setPackage(PLAY_BOOKS_PACKAGE)
    }
    if (startActivity(packageReaderIntent)) return

    context.startActivity(Intent(Intent.ACTION_VIEW, readerUri))
  }

  private fun startActivity(intent: Intent): Boolean = try {
    context.startActivity(intent)
    true
  } catch (_: ActivityNotFoundException) {
    false
  }
}

internal fun googlePlayBooksReaderUrl(url: String): String? {
  val query = url.substringAfter('?', missingDelimiterValue = "").substringBefore('#')
  val encodedVolumeId = query.split('&').firstNotNullOfOrNull { parameter ->
    val separator = parameter.indexOf('=')
    if (separator <= 0) return@firstNotNullOfOrNull null
    if (!parameter.substring(0, separator).equals("id", ignoreCase = true)) {
      return@firstNotNullOfOrNull null
    }
    parameter.substring(separator + 1).takeIf(String::isNotBlank)
  } ?: return null
  return "$PLAY_BOOKS_READER_URL_PREFIX$encodedVolumeId"
}

private fun Uri.isGoogleBooksUri(): Boolean {
  val normalizedHost = host?.lowercase() ?: return false
  val normalizedPath = path.orEmpty().lowercase()
  return when {
    normalizedHost == "play.google.com" ->
      normalizedPath.startsWith("/books") || normalizedPath.startsWith("/store/books")
    normalizedHost == "books.google.com" -> true
    normalizedHost.startsWith("books.google.") -> true
    else -> false
  }
}

private fun InputStream.readUpTo(limit: Int): ByteArray {
  val output = ByteArrayOutputStream(minOf(limit, DEFAULT_BUFFER_SIZE))
  val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
  var total = 0
  while (total < limit) {
    val read = read(buffer, 0, minOf(buffer.size, limit - total))
    if (read < 0) break
    output.write(buffer, 0, read)
    total += read
  }
  return output.toByteArray()
}

private const val PLAY_BOOKS_PACKAGE = "com.google.android.apps.books"
private const val PLAY_BOOKS_READER_ACTIVITY =
  "com.google.android.apps.play.books.ebook.activity.ReadingActivity"
private const val PLAY_BOOKS_READER_URL_PREFIX = "https://play.google.com/books/reader?id="
private const val MAX_AMAZON_IMPORT_BYTES = 25 * 1024 * 1024
