package dev.terashima.yomitorirss.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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

private enum class LibraryTab(val label: String) {
  ALL("全体"),
  SERIES("シリーズ"),
  SETTINGS("設定"),
}

@Composable
fun LibraryScreen(
  state: LibraryUiState,
  onSyncGooglePlayBooks: () -> Unit,
  onHideBook: (LibraryBook) -> Unit,
  onRestoreBook: (LibraryBook) -> Unit,
  onSetBookSeries: (LibraryBook, String, Int?) -> Unit,
  onClearBookSeries: (LibraryBook) -> Unit,
  onDismissMessage: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val snackbarHostState = remember { SnackbarHostState() }
  var selectedTabName by rememberSaveable { mutableStateOf(LibraryTab.ALL.name) }
  var seriesEditorBook by remember { mutableStateOf<LibraryBook?>(null) }
  var expandedSeries by remember { mutableStateOf(emptySet<String>()) }
  val selectedTab = LibraryTab.valueOf(selectedTabName)
  val existingSeriesNames = remember(state.books, state.hiddenBooks) {
    (state.books + state.hiddenBooks)
      .mapNotNull { it.series?.name?.trim()?.takeIf(String::isNotEmpty) }
      .distinctBy { it.lowercase() }
      .sortedBy { it.lowercase() }
  }

  LaunchedEffect(state.message) {
    val message = state.message ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(message)
    onDismissMessage()
  }

  seriesEditorBook?.let { book ->
    LibrarySeriesDialog(
      book = book,
      existingSeriesNames = existingSeriesNames,
      onDismiss = { seriesEditorBook = null },
      onSave = { name, position ->
        onSetBookSeries(book, name, position)
        seriesEditorBook = null
      },
      onClear = {
        onClearBookSeries(book)
        seriesEditorBook = null
      },
    )
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    snackbarHost = { SnackbarHost(snackbarHostState) },
    bottomBar = {
      NavigationBar(windowInsets = WindowInsets(0, 0, 0, 0)) {
        LibraryTab.entries.forEach { tab ->
          NavigationBarItem(
            selected = selectedTab == tab,
            onClick = { selectedTabName = tab.name },
            icon = {
              Icon(
                imageVector = when (tab) {
                  LibraryTab.ALL -> Icons.Default.LibraryBooks
                  LibraryTab.SERIES -> Icons.Default.Folder
                  LibraryTab.SETTINGS -> Icons.Default.Settings
                },
                contentDescription = tab.label,
              )
            },
            label = { Text(tab.label, maxLines = 1) },
          )
        }
      }
    },
  ) { padding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding),
    ) {
      if (!state.initialized) {
        CircularProgressIndicator(Modifier.align(Alignment.Center))
      } else {
        when (selectedTab) {
          LibraryTab.ALL -> LibraryAllTab(
            books = state.books,
            hiddenCount = state.hiddenBooks.size,
            onHideBook = onHideBook,
            onEditSeries = { seriesEditorBook = it },
          )

          LibraryTab.SERIES -> LibrarySeriesTab(
            books = state.books,
            hiddenCount = state.hiddenBooks.size,
            expandedSeries = expandedSeries,
            onExpandedSeriesChange = { expandedSeries = it },
            onHideBook = onHideBook,
            onEditSeries = { seriesEditorBook = it },
          )

          LibraryTab.SETTINGS -> LibrarySettingsTab(
            state = state,
            onSyncGooglePlayBooks = onSyncGooglePlayBooks,
            onRestoreBook = onRestoreBook,
            onEditSeries = { seriesEditorBook = it },
          )
        }
      }
    }
  }
}

@Composable
private fun LibraryAllTab(
  books: List<LibraryBook>,
  hiddenCount: Int,
  onHideBook: (LibraryBook) -> Unit,
  onEditSeries: (LibraryBook) -> Unit,
) {
  if (books.isEmpty()) {
    LibraryEmptyMessage(
      if (hiddenCount > 0) {
        "表示中の蔵書はありません。設定に非表示の蔵書が $hiddenCount 冊あります。"
      } else {
        "蔵書がありません。設定から Google Play Books を同期してください。"
      },
    )
    return
  }

  val sortedBooks = remember(books) {
    books.sortedWith(compareBy<LibraryBook> { it.title.lowercase() }.thenBy { it.sourceId })
  }
  LibraryBookGrid(
    books = sortedBooks,
    actionLabel = "非表示",
    onAction = onHideBook,
    onEditSeries = onEditSeries,
  )
}

@Composable
private fun LibrarySeriesTab(
  books: List<LibraryBook>,
  hiddenCount: Int,
  expandedSeries: Set<String>,
  onExpandedSeriesChange: (Set<String>) -> Unit,
  onHideBook: (LibraryBook) -> Unit,
  onEditSeries: (LibraryBook) -> Unit,
) {
  if (books.isEmpty()) {
    LibraryEmptyMessage(
      if (hiddenCount > 0) {
        "表示中の蔵書はありません。設定に非表示の蔵書が $hiddenCount 冊あります。"
      } else {
        "蔵書がありません。設定から Google Play Books を同期してください。"
      },
    )
    return
  }

  val groups = remember(books) { groupLibraryBooks(books) }
  LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = 112.dp),
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(12.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    groups.series.forEach { section ->
      val expanded = section.name in expandedSeries
      item(
        key = "series:${section.name}",
        span = { GridItemSpan(maxLineSpan) },
      ) {
        LibrarySeriesHeader(
          name = section.name,
          count = section.books.size,
          expanded = expanded,
          onToggle = {
            onExpandedSeriesChange(
              if (expanded) {
                expandedSeries - section.name
              } else {
                expandedSeries + section.name
              },
            )
          },
        )
      }
      if (expanded) {
        items(
          items = section.books,
          key = { book -> "${book.source.name}:${book.sourceId}" },
        ) { book ->
          LibraryBookThumbnail(
            book = book,
            actionLabel = "非表示",
            onAction = { onHideBook(book) },
            onEditSeries = { onEditSeries(book) },
          )
        }
      }
    }

    if (groups.ungrouped.isNotEmpty()) {
      if (groups.series.isNotEmpty()) {
        item(
          key = "ungrouped-heading",
          span = { GridItemSpan(maxLineSpan) },
        ) {
          Text(
            "シリーズ未設定",
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      items(
        items = groups.ungrouped,
        key = { book -> "${book.source.name}:${book.sourceId}" },
      ) { book ->
        LibraryBookThumbnail(
          book = book,
          actionLabel = "非表示",
          onAction = { onHideBook(book) },
          onEditSeries = { onEditSeries(book) },
        )
      }
    }
  }
}

@Composable
private fun LibrarySettingsTab(
  state: LibraryUiState,
  onSyncGooglePlayBooks: () -> Unit,
  onRestoreBook: (LibraryBook) -> Unit,
  onEditSeries: (LibraryBook) -> Unit,
) {
  Column(Modifier.fillMaxSize()) {
    LibrarySyncHeader(
      state = state,
      onSyncGooglePlayBooks = onSyncGooglePlayBooks,
    )
    HorizontalDivider()
    Column(
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text("蔵書の表示", style = MaterialTheme.typography.titleMedium)
      Text(
        "表示中 ${state.books.size} 冊 / 非表示 ${state.hiddenBooks.size} 冊",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        "全体・シリーズで非表示にした書籍は、ここから再表示できます。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    HorizontalDivider()

    if (state.hiddenBooks.isEmpty()) {
      Text(
        "非表示の蔵書はありません。",
        modifier = Modifier.padding(24.dp),
        style = MaterialTheme.typography.bodyMedium,
      )
    } else {
      Text(
        "非表示の蔵書",
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        style = MaterialTheme.typography.titleMedium,
      )
      LibraryBookGrid(
        books = state.hiddenBooks,
        actionLabel = "再表示",
        onAction = onRestoreBook,
        onEditSeries = onEditSeries,
        modifier = Modifier.weight(1f),
      )
    }
  }
}

@Composable
private fun LibraryBookGrid(
  books: List<LibraryBook>,
  actionLabel: String,
  onAction: (LibraryBook) -> Unit,
  onEditSeries: (LibraryBook) -> Unit,
  modifier: Modifier = Modifier,
) {
  LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = 112.dp),
    modifier = modifier.fillMaxWidth(),
    contentPadding = PaddingValues(12.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    items(
      items = books,
      key = { book -> "${book.source.name}:${book.sourceId}" },
    ) { book ->
      LibraryBookThumbnail(
        book = book,
        actionLabel = actionLabel,
        onAction = { onAction(book) },
        onEditSeries = { onEditSeries(book) },
      )
    }
  }
}

@Composable
private fun LibraryEmptyMessage(message: String) {
  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.TopStart,
  ) {
    Text(
      message,
      modifier = Modifier.padding(24.dp),
      style = MaterialTheme.typography.bodyMedium,
    )
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
private fun LibrarySeriesHeader(
  name: String,
  count: Int,
  expanded: Boolean,
  onToggle: () -> Unit,
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onToggle),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(Modifier.weight(1f)) {
        Text(
          name,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          "$count 冊",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Text(
        if (expanded) "閉じる" else "展開",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
      )
    }
  }
}

@Composable
private fun LibraryBookThumbnail(
  book: LibraryBook,
  actionLabel: String,
  onAction: () -> Unit,
  onEditSeries: () -> Unit,
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
    book.series?.position?.let { position ->
      Text(
        "$position 巻",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
      )
    }
    if (book.authors.isNotEmpty()) {
      Text(
        book.authors.joinToString(", "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      TextButton(onClick = onEditSeries) {
        Text("シリーズ")
      }
      TextButton(onClick = onAction) {
        Text(actionLabel)
      }
    }
  }
}

@Composable
private fun LibrarySeriesDialog(
  book: LibraryBook,
  existingSeriesNames: List<String>,
  onDismiss: () -> Unit,
  onSave: (String, Int?) -> Unit,
  onClear: () -> Unit,
) {
  var seriesName by remember(book.source, book.sourceId, book.series) {
    mutableStateOf(book.series?.name.orEmpty())
  }
  var positionText by remember(book.source, book.sourceId, book.series) {
    mutableStateOf(book.series?.position?.toString().orEmpty())
  }
  var seriesMenuExpanded by remember { mutableStateOf(false) }
  val trimmedName = seriesName.trim()
  val trimmedPosition = positionText.trim()
  val position = trimmedPosition.takeIf(String::isNotEmpty)?.toIntOrNull()
  val positionValid = trimmedPosition.isEmpty() || (position != null && position > 0)
  val canSave = trimmedName.isNotEmpty() && positionValid

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("シリーズを設定") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          book.title,
          style = MaterialTheme.typography.bodyMedium,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        OutlinedTextField(
          value = seriesName,
          onValueChange = { seriesName = it },
          modifier = Modifier.fillMaxWidth(),
          label = { Text("シリーズ名") },
          singleLine = true,
        )
        if (existingSeriesNames.isNotEmpty()) {
          Box {
            TextButton(onClick = { seriesMenuExpanded = true }) {
              Text("既存のシリーズから選択")
            }
            DropdownMenu(
              expanded = seriesMenuExpanded,
              onDismissRequest = { seriesMenuExpanded = false },
            ) {
              existingSeriesNames.forEach { name ->
                DropdownMenuItem(
                  text = { Text(name) },
                  onClick = {
                    seriesName = name
                    seriesMenuExpanded = false
                  },
                )
              }
            }
          }
        }
        OutlinedTextField(
          value = positionText,
          onValueChange = { value ->
            positionText = value.filter(Char::isDigit)
          },
          modifier = Modifier.fillMaxWidth(),
          label = { Text("巻数・順番（任意）") },
          supportingText = {
            Text(if (positionValid) "同じシリーズ内ではこの順番で表示します" else "1以上で入力してください")
          },
          isError = !positionValid,
          singleLine = true,
        )
      }
    },
    confirmButton = {
      TextButton(
        enabled = canSave,
        onClick = { onSave(trimmedName, position) },
      ) {
        Text("保存")
      }
    },
    dismissButton = {
      Row {
        if (book.series != null) {
          TextButton(onClick = onClear) {
            Text("シリーズ解除")
          }
        }
        TextButton(onClick = onDismiss) {
          Text("キャンセル")
        }
      }
    },
  )
}

private fun formatSyncTime(epochMillis: Long): String {
  val formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
  val local = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
  return "最終同期: ${formatter.format(local)}"
}
