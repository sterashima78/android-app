package dev.terashima.yomitorirss.feature.library

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import java.net.URI

@Composable
fun LibraryFeatureRoute(
  viewModel: LibraryViewModel,
  organizationViewModel: LibraryOrganizationViewModel,
  onSyncGooglePlayBooks: () -> Unit,
  onOpenSmbBook: (LibraryBook) -> Unit,
  modifier: Modifier = Modifier,
) {
  val state by viewModel.state.collectAsState()
  val organizationState by organizationViewModel.state.collectAsState()
  var organizationVisible by rememberSaveable { mutableStateOf(false) }
  val context = LocalContext.current
  val booksBySourceId = remember(state.books, state.hiddenBooks) {
    (state.books + state.hiddenBooks)
      .filter { it.source == LibrarySource.SMB }
      .associateBy(LibraryBook::sourceId)
  }
  val libraryUriHandler = remember(context, booksBySourceId, onOpenSmbBook) {
    LibraryUriHandler(context) { sourceId ->
      booksBySourceId[sourceId]?.let(onOpenSmbBook)
        ?: Toast.makeText(context, "SMB書籍が見つかりません", Toast.LENGTH_LONG).show()
    }
  }
  val webLibraryImportHandler = remember(viewModel) {
    { source: LibrarySource, json: String -> viewModel.importAmazonLibraryJson(source, json) }
  }
  val smbBinding = remember(state, viewModel) {
    SmbLibraryUiBinding(
      state = state,
      onSync = viewModel::syncSmbLibrary,
      onSave = viewModel::saveSmbServer,
      onDelete = viewModel::deleteSmbServer,
    )
  }
  val smbBookFileActionBinding = remember(viewModel) {
    SmbBookFileActionBinding(
      onRename = viewModel::renameSmbBook,
      onDelete = viewModel::deleteSmbBook,
    )
  }

  CompositionLocalProvider(
    LocalUriHandler provides libraryUriHandler,
    LocalWebLibraryImportHandler provides webLibraryImportHandler,
    LocalSmbLibraryUiBinding provides smbBinding,
    LocalSmbBookFileActionBinding provides smbBookFileActionBinding,
  ) {
    LibraryScreen(
      modifier = modifier.fillMaxSize(),
      state = state,
      onSyncGooglePlayBooks = onSyncGooglePlayBooks,
      onHideBook = viewModel::hideBook,
      onRestoreBook = viewModel::restoreBook,
      onSetBookSeries = viewModel::setBookSeries,
      onClearBookSeries = viewModel::clearBookSeries,
      onOpenOrganization = {
        organizationViewModel.refresh()
        organizationVisible = true
      },
      onDismissMessage = viewModel::dismissMessage,
    )
  }

  if (organizationVisible) {
    LibraryMetadataManagementDialog(
      books = state.books,
      state = organizationState,
      onSave = organizationViewModel::save,
      onSuggest = organizationViewModel::suggest,
      onStartBatch = organizationViewModel::startBatch,
      onPauseBatch = organizationViewModel::pauseBatch,
      onResumeBatch = organizationViewModel::resumeBatch,
      onReorganizeSeries = organizationViewModel::reorganizeSeries,
      onDismissMessage = organizationViewModel::dismissMessage,
      onDismiss = { organizationVisible = false },
    )
  }
}

private class LibraryUriHandler(
  private val context: Context,
  private val onOpenSmbBook: (String) -> Unit,
) : UriHandler {
  override fun openUri(uri: String) {
    val parsedUri = Uri.parse(uri)
    if (isSmbBookOpenUri(parsedUri)) {
      val sourceId = parsedUri.getQueryParameter("sourceId")?.trim().orEmpty()
      if (sourceId.isNotEmpty()) onOpenSmbBook(sourceId)
      return
    }
    if (isKindlePersonalDocumentOpenUri(parsedUri)) {
      openKindlePersonalDocument(parsedUri)
      return
    }

    when (googleBooksLinkType(uri)) {
      GoogleBooksLinkType.READER -> openGooglePlayBooksReader(parsedUri)
      GoogleBooksLinkType.PLAY_BOOKS_HOME -> {
        if (!openGooglePlayBooksHome()) showPlayBooksOpenFailedMessage()
      }
      GoogleBooksLinkType.INFORMATION -> showMissingReaderLinkMessage()
      GoogleBooksLinkType.OTHER -> context.startActivity(Intent(Intent.ACTION_VIEW, parsedUri))
    }
  }

  private fun openKindlePersonalDocument(uri: Uri) {
    val title = uri.getQueryParameter("title")?.trim().orEmpty()
    val launchIntent = context.packageManager.getLaunchIntentForPackage(KINDLE_PACKAGE)
    val launched = launchIntent != null && startActivity(launchIntent)

    if (title.isNotEmpty()) {
      val clipboard = context.getSystemService(ClipboardManager::class.java)
      clipboard.setPrimaryClip(ClipData.newPlainText("Kindle Personal Document title", title))
    }

    val message = when {
      title.isEmpty() && launched -> "Kindleを開きました"
      title.isEmpty() -> "Kindleアプリを開けませんでした"
      launched -> "タイトルをコピーしました。Kindleで検索してください"
      else -> "タイトルをコピーしました。Kindleアプリを開けませんでした"
    }
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
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

private fun isSmbBookOpenUri(uri: Uri): Boolean =
  uri.scheme == "yomitori" && uri.host == "smb-book" && uri.path == "/open"

private fun isKindlePersonalDocumentOpenUri(uri: Uri): Boolean =
  uri.scheme == "yomitori" && uri.host == "kindle-personal-document" && uri.path == "/open"

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

private const val KINDLE_PACKAGE = "com.amazon.kindle"
private const val PLAY_BOOKS_PACKAGE = "com.google.android.apps.books"
private const val HTTP_SCHEME_PREFIX = "http://"
private const val PLAY_BOOKS_HTTP_READER_PREFIX = "http://play.google.com/books/reader"
private const val GOOGLE_BOOKS_NO_READER_MESSAGE =
  "この項目には Google Books API の読書リンクがないため、直接開けません。"
private const val PLAY_BOOKS_OPEN_FAILED_MESSAGE =
  "Google Play Books を開けませんでした。"
