package dev.terashima.yomitorirss.feature.library

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.terashima.yomitorirss.YomitoriApplication
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.library.data.GoogleBooksAuthorizationManager
import dev.terashima.yomitorirss.feature.library.data.GoogleBooksAuthorizationOutcome
import dev.terashima.yomitorirss.feature.library.data.SeriesAwareLibraryRepository
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LibraryRoute(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val application = context.applicationContext as YomitoriApplication
  val authorization = remember(application) { GoogleBooksAuthorizationManager(application) }
  val repository = remember(application) {
    SeriesAwareLibraryRepository(DatabaseConnection(application.container.database))
  }
  val coverScheduler = remember(application) { KindleCoverEnrichmentScheduler(application) }
  val audibleCoverScheduler = remember(application) { AudibleCoverEnrichmentScheduler(application) }
  val libraryViewModel: LibraryViewModel = viewModel(
    factory = LibraryViewModel.Factory(repository),
  )
  val state by libraryViewModel.state.collectAsState()
  val coverWorkInfos by coverScheduler.workInfos.collectAsState(initial = emptyList())
  val audibleCoverWorkInfos by audibleCoverScheduler.workInfos.collectAsState(initial = emptyList())
  var coverSnapshot by remember { mutableStateOf<LibrarySnapshot?>(null) }
  var showCoverQueue by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  val libraryUriHandler = remember(context) { LibraryUriHandler(context) }

  LaunchedEffect(
    state.initialized,
    state.kindleCoverEnrichmentEnabled,
    state.sourceStates[LibrarySource.KINDLE]?.lastSyncedAtEpochMillis,
    state.sourceStates[LibrarySource.AUDIBLE]?.lastSyncedAtEpochMillis,
  ) {
    if (state.initialized) {
      coverScheduler.sync(state.kindleCoverEnrichmentEnabled)
      audibleCoverScheduler.schedule()
    }
  }

  LaunchedEffect(state.books, state.hiddenBooks, state.sourceStates, state.kindleCoverEnrichmentEnabled) {
    coverSnapshot = null
  }

  LaunchedEffect(coverWorkInfos, audibleCoverWorkInfos) {
    val allCoverWorkInfos = coverWorkInfos + audibleCoverWorkInfos
    if (allCoverWorkInfos.isNotEmpty() && allCoverWorkInfos.all { it.state.isFinished }) {
      coverSnapshot = withContext(Dispatchers.IO) { repository.snapshot() }
    }
  }

  val displayState = coverSnapshot?.let { snapshot ->
    state.copy(
      books = snapshot.books,
      hiddenBooks = snapshot.hiddenBooks,
      sourceStates = snapshot.sourceStates,
      kindleCoverEnrichmentEnabled = snapshot.kindleCoverEnrichmentEnabled,
    )
  } ?: state

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
    "application/zip",
    "application/octet-stream",
  )
  val audibleImportMimeTypes = arrayOf(
    "text/csv",
    "application/zip",
    "application/octet-stream",
  )

  CompositionLocalProvider(LocalUriHandler provides libraryUriHandler) {
    LibraryScreen(
      modifier = modifier,
      state = displayState,
      onSyncGooglePlayBooks = requestSync,
      onImportKindle = { kindleImportLauncher.launch(kindleImportMimeTypes) },
      onImportAudible = { audibleImportLauncher.launch(audibleImportMimeTypes) },
      onHideBook = libraryViewModel::hideBook,
      onRestoreBook = libraryViewModel::restoreBook,
      onSetBookSeries = libraryViewModel::setBookSeries,
      onClearBookSeries = libraryViewModel::clearBookSeries,
      onKindleCoverEnrichmentEnabledChange = libraryViewModel::setKindleCoverEnrichmentEnabled,
      onOpenCoverQueue = { showCoverQueue = true },
      onDismissMessage = libraryViewModel::dismissMessage,
    )
  }

  if (showCoverQueue) {
    LibraryCoverQueueRoute(onDismiss = { showCoverQueue = false })
  }
}

private class LibraryUriHandler(
  private val context: Context,
) : UriHandler {
  override fun openUri(uri: String) {
    when (googleBooksLinkType(uri)) {
      GoogleBooksLinkType.READER -> openGooglePlayBooksReader(Uri.parse(uri))
      GoogleBooksLinkType.PLAY_BOOKS_HOME -> {
        if (!openGooglePlayBooksHome()) showPlayBooksOpenFailedMessage()
      }
      GoogleBooksLinkType.INFORMATION -> showMissingReaderLinkMessage()
      GoogleBooksLinkType.OTHER -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
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

    if (openGooglePlayBooksHome()) return

    showPlayBooksOpenFailedMessage()
  }

  private fun openGooglePlayBooksHome(): Boolean {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(PLAY_BOOKS_PACKAGE)
      ?: return false
    return startActivity(launchIntent)
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

  private fun showMissingReaderLinkMessage() {
    Toast.makeText(context, GOOGLE_BOOKS_NO_READER_MESSAGE, Toast.LENGTH_LONG).show()
  }

  private fun showPlayBooksOpenFailedMessage() {
    Toast.makeText(context, PLAY_BOOKS_OPEN_FAILED_MESSAGE, Toast.LENGTH_LONG).show()
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

internal enum class GoogleBooksLinkType {
  READER,
  PLAY_BOOKS_HOME,
  INFORMATION,
  OTHER,
}

internal fun googleBooksLinkType(url: String): GoogleBooksLinkType {
  val uri = runCatching { URI(url) }.getOrNull() ?: return GoogleBooksLinkType.OTHER
  val scheme = uri.scheme?.lowercase()
  if (scheme != "http" && scheme != "https") return GoogleBooksLinkType.OTHER

  val host = uri.host?.lowercase() ?: return GoogleBooksLinkType.OTHER
  val path = uri.path.orEmpty().lowercase()
  return when {
    host == "play.google.com" && path.trimEnd('/') == "/books" ->
      GoogleBooksLinkType.PLAY_BOOKS_HOME
    host == "play.google.com" && path.startsWith("/books/reader") -> GoogleBooksLinkType.READER
    host == "play.google.com" &&
      (path.startsWith("/books") || path.startsWith("/store/books")) ->
      GoogleBooksLinkType.INFORMATION
    host == "books.google.com" || host.startsWith("books.google.") -> GoogleBooksLinkType.INFORMATION
    else -> GoogleBooksLinkType.OTHER
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

private const val PLAY_BOOKS_PACKAGE = "com.google.android.apps.books"
private const val LEGACY_PLAY_BOOKS_READER_ACTIVITY =
  "com.google.android.apps.play.books.ebook.activity.ReadingActivity"
private const val HTTP_SCHEME_PREFIX = "http://"
private const val PLAY_BOOKS_HTTP_READER_PREFIX = "http://play.google.com/books/reader"
private const val GOOGLE_BOOKS_NO_READER_MESSAGE =
  "この項目には Google Books API の読書リンクがないため、直接開けません。"
private const val PLAY_BOOKS_OPEN_FAILED_MESSAGE =
  "Google Play Books を開けませんでした。"
