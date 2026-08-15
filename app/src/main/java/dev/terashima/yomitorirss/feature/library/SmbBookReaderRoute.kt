package dev.terashima.yomitorirss.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.terashima.yomitorirss.feature.bookreader.BookDocument
import dev.terashima.yomitorirss.feature.bookreader.BookFormat
import dev.terashima.yomitorirss.feature.bookreader.data.DefaultBookPageSourceFactory
import dev.terashima.yomitorirss.feature.bookreader.data.SharedPreferencesReadingPositionStore
import dev.terashima.yomitorirss.feature.bookreader.ui.BookReaderScreen
import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.PreparedLibraryBook
import dev.terashima.yomitorirss.feature.library.SmbBookFormat
import dev.terashima.yomitorirss.feature.library.SmbLibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

@Composable
internal fun SmbBookReaderRoute(
  book: LibraryBook,
  repository: SmbLibraryRepository,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onBack)

  val context = LocalContext.current
  val progress = remember(book.sourceId) { MutableStateFlow(DownloadProgress()) }
  val currentProgress by progress.collectAsState()
  var prepared by remember(book.sourceId) { mutableStateOf<PreparedLibraryBook?>(null) }
  var error by remember(book.sourceId) { mutableStateOf<String?>(null) }
  var retryKey by remember(book.sourceId) { mutableStateOf(0) }

  LaunchedEffect(book.sourceId, retryKey) {
    prepared = null
    error = null
    progress.value = DownloadProgress()
    runCatching {
      withContext(Dispatchers.IO) {
        repository.prepareBook(book) { downloaded, total ->
          progress.value = DownloadProgress(downloaded, total)
        }
      }
    }
      .onSuccess { prepared = it }
      .onFailure { error = it.message ?: "書籍を取得できませんでした" }
  }

  val ready = prepared
  if (ready == null) {
    BookPreparationScreen(
      title = book.title,
      progress = currentProgress,
      error = error,
      onRetry = { retryKey += 1 },
      onBack = onBack,
      modifier = modifier,
    )
    return
  }

  val document = remember(ready) {
    BookDocument(
      id = ready.sourceId,
      title = ready.title,
      format = when (ready.format) {
        SmbBookFormat.ZIP -> BookFormat.ZIP
        SmbBookFormat.PDF -> BookFormat.PDF
      },
      localPath = ready.localPath,
    )
  }
  val sourceResult = remember(document) {
    runCatching { DefaultBookPageSourceFactory().open(document) }
  }
  val source = sourceResult.getOrNull()
  if (source == null) {
    BookPreparationScreen(
      title = book.title,
      progress = DownloadProgress(),
      error = sourceResult.exceptionOrNull()?.message ?: "書籍を開けませんでした",
      onRetry = { retryKey += 1 },
      onBack = onBack,
      modifier = modifier,
    )
    return
  }

  DisposableEffect(source) {
    onDispose { source.close() }
  }
  val positionStore = remember(context) { SharedPreferencesReadingPositionStore(context.applicationContext) }
  BookReaderScreen(
    document = document,
    source = source,
    positionStore = positionStore,
    onBack = onBack,
    modifier = modifier,
  )
}

@Composable
private fun BookPreparationScreen(
  title: String,
  progress: DownloadProgress,
  error: String?,
  onRetry: () -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .padding(24.dp),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(title, style = MaterialTheme.typography.titleLarge)
      if (error == null) {
        CircularProgressIndicator()
        val total = progress.totalBytes
        if (total > 0L) {
          LinearProgressIndicator(
            progress = { (progress.downloadedBytes.toFloat() / total).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
          )
          Text(
            "${formatBytes(progress.downloadedBytes)} / ${formatBytes(total)}",
            style = MaterialTheme.typography.bodySmall,
          )
        } else {
          Text("ファイルを準備しています")
        }
      } else {
        Text(error, color = MaterialTheme.colorScheme.error)
        Button(onClick = onRetry) { Text("再試行") }
      }
      TextButton(onClick = onBack) { Text("蔵書に戻る") }
    }
  }
}

private data class DownloadProgress(
  val downloadedBytes: Long = 0L,
  val totalBytes: Long = 0L,
)

private fun formatBytes(bytes: Long): String = when {
  bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
  bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
  bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
  else -> "$bytes B"
}
