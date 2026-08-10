package dev.terashima.yomitorirss.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun LibraryScreen(
  state: LibraryUiState,
  onSyncGooglePlayBooks: () -> Unit,
  onHideBook: (LibraryBook) -> Unit,
  onRestoreBook: (LibraryBook) -> Unit,
  onDismissMessage: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val snackbarHostState = remember { SnackbarHostState() }
  var showingHidden by rememberSaveable { mutableStateOf(false) }
  LaunchedEffect(state.message) {
    val message = state.message ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(message)
    onDismissMessage()
  }

  Column(modifier.fillMaxSize()) {
    SnackbarHost(snackbarHostState)
    if (!state.initialized) {
      Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        CircularProgressIndicator()
      }
      return@Column
    }

    LibrarySyncHeader(
      state = state,
      onSyncGooglePlayBooks = onSyncGooglePlayBooks,
    )
    LibraryVisibilitySelector(
      showingHidden = showingHidden,
      visibleCount = state.books.size,
      hiddenCount = state.hiddenBooks.size,
      onShowVisible = { showingHidden = false },
      onShowHidden = { showingHidden = true },
    )
    HorizontalDivider()

    val displayedBooks = if (showingHidden) state.hiddenBooks else state.books
    if (displayedBooks.isEmpty()) {
      Text(
        if (showingHidden) {
          "非表示の蔵書はありません。"
        } else if (state.hiddenBooks.isNotEmpty()) {
          "表示中の蔵書はありません。非表示の蔵書が ${state.hiddenBooks.size} 冊あります。"
        } else {
          "蔵書がありません。Google Play Books を同期してください。"
        },
        modifier = Modifier.padding(24.dp),
        style = MaterialTheme.typography.bodyMedium,
      )
    } else {
      LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 112.dp),
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        items(
          items = displayedBooks,
          key = { book -> "${book.source.name}:${book.sourceId}" },
        ) { book ->
          LibraryBookThumbnail(
            book = book,
            actionLabel = if (showingHidden) "再表示" else "非表示",
            onAction = {
              if (showingHidden) onRestoreBook(book) else onHideBook(book)
            },
          )
        }
      }
    }
  }
}

@Composable
private fun LibrarySyncHeader(
  state: LibraryUiState,
  onSyncGooglePlayBooks: () -> Unit,
) {
  Column(
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(Modifier.weight(1f)) {
        Text("Google Play Books", style = MaterialTheme.typography.titleMedium)
        val sourceState = state.sourceStates[LibrarySource.GOOGLE_PLAY_BOOKS]
        Text(
          sourceState?.lastSyncedAtEpochMillis?.let(::formatSyncTime) ?: "未同期",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        sourceState?.accountLabel?.let { account ->
          Text(
            account,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      Button(
        onClick = onSyncGooglePlayBooks,
        enabled = !state.syncing,
      ) {
        if (state.syncing) {
          CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
          Text("同期")
        }
      }
    }

    Text(
      "Kindle と Audible は将来ファイルインポートに対応予定です。",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun LibraryVisibilitySelector(
  showingHidden: Boolean,
  visibleCount: Int,
  hiddenCount: Int,
  onShowVisible: () -> Unit,
  onShowHidden: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 12.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    TextButton(onClick = onShowVisible) {
      Text(
        "蔵書 ($visibleCount)",
        fontWeight = if (showingHidden) FontWeight.Normal else FontWeight.Bold,
      )
    }
    TextButton(onClick = onShowHidden) {
      Text(
        "非表示 ($hiddenCount)",
        fontWeight = if (showingHidden) FontWeight.Bold else FontWeight.Normal,
      )
    }
  }
}

@Composable
private fun LibraryBookThumbnail(
  book: LibraryBook,
  actionLabel: String,
  onAction: () -> Unit,
) {
  val uriHandler = LocalUriHandler.current
  Column(modifier = Modifier.fillMaxWidth()) {
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(0.68f)
        .clickable(enabled = !book.infoUrl.isNullOrBlank()) {
          book.infoUrl?.let(uriHandler::openUri)
        },
    ) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          "表紙なし",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        book.thumbnailUrl?.takeIf(String::isNotBlank)?.let { thumbnailUrl ->
          AsyncImage(
            model = thumbnailUrl,
            contentDescription = "${book.title} の表紙",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
          )
        }
      }
    }

    Spacer(Modifier.height(6.dp))
    Text(
      book.title,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.Medium,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
    if (book.authors.isNotEmpty()) {
      Text(
        book.authors.joinToString(", "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    TextButton(
      onClick = onAction,
      modifier = Modifier.align(Alignment.End),
    ) {
      Text(actionLabel)
    }
  }
}

private fun formatSyncTime(epochMillis: Long): String {
  val formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
  val local = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
  return "最終同期: ${formatter.format(local)}"
}
