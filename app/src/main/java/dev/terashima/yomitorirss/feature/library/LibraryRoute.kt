package dev.terashima.yomitorirss.feature.library

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
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
    when {
      parsedUri.isGoogleBooksReaderUri() -> openGooglePlayBooksReader(parsedUri)
      else -> context.startActivity(Intent(Intent.ACTION_VIEW, parsedUri))
    }
  }

  private fun openGooglePlayBooksReader(uri: Uri) {
    val readerUri = Uri.parse(normalizeGooglePlayBooksReaderUrl(uri.toString()))
    val readerIntent = Intent(Intent.ACTION_VIEW, readerUri).apply {
      setPackage(PLAY_BOOKS_PACKAGE)
    }

    readerActivities(readerUri).forEach { component ->
      val explicitIntent = Intent(readerIntent).apply { this.component = component }
      if (startActivity(explicitIntent)) return
    }

    val legacyReaderIntent = Intent(readerIntent).apply {
      component = ComponentName(PLAY_BOOKS_PACKAGE, LEGACY_PLAY_BOOKS_READER_ACTIVITY)
    }
    if (startActivity(legacyReaderIntent)) return

    val launchIntent = context.packageManager.getLaunchIntentForPackage(PLAY_BOOKS_PACKAGE)
    if (launchIntent != null && startActivity(launchIntent)) return

    context.startActivity(Intent(Intent.ACTION_VIEW, readerUri))
  }

  private fun readerActivities(readerUri: Uri): List<ComponentName> {
    val packageManager = context.packageManager
    val matchedActivities = packageManager.queryIntentActivities(
      Intent(Intent.ACTION_VIEW, readerUri).apply { setPackage(PLAY_BOOKS_PACKAGE) },
      PackageManager.MATCH_DEFAULT_ONLY,
    ).mapNotNull { resolveInfo ->
      val activity = resolveInfo.activityInfo ?: return@mapNotNull null
      if (!activity.exported || readerActivityScore(activity.name) <= 0) return@mapNotNull null
      ComponentName(activity.packageName, activity.name)
    }

    val declaredActivities = playBooksActivities(packageManager)
      .asSequence()
      .filter(ActivityInfo::exported)
      .filter { readerActivityScore(it.name) > 0 }
      .map { ComponentName(it.packageName, it.name) }
      .toList()

    return (matchedActivities + declaredActivities)
      .distinctBy { it.flattenToString() }
      .sortedByDescending { readerActivityScore(it.className) }
  }

  @Suppress("DEPRECATION")
  private fun playBooksActivities(packageManager: PackageManager): List<ActivityInfo> = try {
    packageManager.getPackageInfo(PLAY_BOOKS_PACKAGE, PackageManager.GET_ACTIVITIES)
      .activities
      ?.toList()
      .orEmpty()
  } catch (_: PackageManager.NameNotFoundException) {
    emptyList()
  }

  private fun startActivity(intent: Intent): Boolean = try {
    context.startActivity(intent)
    true
  } catch (_: ActivityNotFoundException) {
    false
  } catch (_: SecurityException) {
    false
  }
}

internal fun normalizeGooglePlayBooksReaderUrl(url: String): String {
  if (!url.startsWith(PLAY_BOOKS_HTTP_READER_PREFIX, ignoreCase = true)) return url
  return "https://${url.substring(HTTP_SCHEME_PREFIX.length)}"
}

internal fun readerActivityScore(activityName: String): Int {
  val normalized = activityName.lowercase()
  var score = 0
  if ("readingactivity" in normalized) score += 120
  if ("readeractivity" in normalized) score += 110
  if ("readactivity" in normalized) score += 100
  if ("reading" in normalized) score += 80
  if ("reader" in normalized) score += 70
  if ("ebook" in normalized) score += 40
  if ("store" in normalized || "shop" in normalized || "catalog" in normalized) score -= 200
  if ("detail" in normalized || "preview" in normalized) score -= 100
  return score
}

private fun Uri.isGoogleBooksReaderUri(): Boolean {
  val normalizedHost = host?.lowercase() ?: return false
  val normalizedPath = path.orEmpty().lowercase()
  return normalizedHost == "play.google.com" && normalizedPath.startsWith("/books/reader")
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
private const val LEGACY_PLAY_BOOKS_READER_ACTIVITY =
  "com.google.android.apps.play.books.ebook.activity.ReadingActivity"
private const val HTTP_SCHEME_PREFIX = "http://"
private const val PLAY_BOOKS_HTTP_READER_PREFIX = "http://play.google.com/books/reader"
private const val MAX_AMAZON_IMPORT_BYTES = 25 * 1024 * 1024
