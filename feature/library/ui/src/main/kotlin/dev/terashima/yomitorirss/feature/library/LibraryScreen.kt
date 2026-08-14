package dev.terashima.yomitorirss.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
import java.util.Locale

private enum class LibraryTab(val label: String) {
  ALL("全体"),
  SERIES("シリーズ"),
  HIDDEN("非表示"),
  SETTINGS("設定"),
}

@Composable
fun LibraryScreen(
  state: LibraryUiState,
  onSyncGooglePlayBooks: () -> Unit,
  onImportKindle: () -> Unit,
  onImportAudible: () -> Unit,
  onHideBook: (LibraryBook) -> Unit,
  onRestoreBook: (LibraryBook) -> Unit,
  onSetBookSeries: (LibraryBook, String, Int?) -> Unit,
  onClearBookSeries: (LibraryBook) -> Unit,
  onDismissMessage: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val snackbarHostState = remember { SnackbarHostState() }
  var selectedTabName by rememberSaveable { mutableStateOf(LibraryTab.ALL.name) }
  var selectedSourceName by rememberSaveable { mutableStateOf<String?>(null) }
  var seriesEditorBook by remember { mutableStateOf<LibraryBook?>(null) }
  var expandedSeries by remember { mutableStateOf(emptySet<String>()) }
  val selectedTab = LibraryTab.valueOf(selectedTabName)
  val selectedSource = remember(selectedSourceName) {
    LibrarySource.entries.firstOrNull { it.name == selectedSourceName }
  }
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
                  LibraryTab.HIDDEN -> Icons.Default.VisibilityOff
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
            selectedSource = selectedSource,
            onSelectedSourceChange = { selectedSourceName = it?.name },
            onHideBook = onHideBook,
            onEditSeries = { seriesEditorBook = it },
          )

          LibraryTab.SERIES -> LibrarySeriesTab(
            books = state.books,
            hiddenCount = state.hiddenBooks.size,
            selectedSource = selectedSource,
            onSelectedSourceChange = { selectedSourceName = it?.name },
            expandedSeries = expandedSeries,
            onExpandedSeriesChange = { expandedSeries = it },
            onHideBook = onHideBook,
            onEditSeries = { seriesEditorBook = it },
          )

          LibraryTab.HIDDEN -> LibraryHiddenTab(
            books = state.hiddenBooks,
            selectedSource = selectedSource,
            onSelectedSourceChange = { selectedSourceName = it?.name },
            onRestoreBook = onRestoreBook,
            onEditSeries = { seriesEditorBook = it },
          )

          LibraryTab.SETTINGS -> LibrarySettingsTab(
            state = state,
            onSyncGooglePlayBooks = onSyncGooglePlayBooks,
            onImportKindle = onImportKindle,
            onImportAudible = onImportAudible,
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
  selectedSource: LibrarySource?,
  onSelectedSourceChange: (LibrarySource?) -> Unit,
  onHideBook: (LibraryBook) -> Unit,
  onEditSeries: (LibraryBook) -> Unit,
) {
  if (books.isEmpty()) {
    LibraryEmptyMessage(
      if (hiddenCount > 0) {
        "表示中の蔵書はありません。非表示タブに $hiddenCount 冊あります。"
      } else {
        "蔵書がありません。設定から蔵書サービスを同期またはインポートしてください。"
      },
    )
    return
  }

  val filteredBooks = remember(books, selectedSource) {
    filterLibraryBooksBySource(books, selectedSource)
  }
  val sortedBooks = remember(filteredBooks) {
    filteredBooks.sortedWith(compareBy<LibraryBook> { it.title.lowercase() }.thenBy { it.sourceId })
  }

  Column(Modifier.fillMaxSize()) {
    LibrarySourceFilterBar(
      selectedSource = selectedSource,
      onSelectedSourceChange = onSelectedSourceChange,
    )
    if (sortedBooks.isEmpty()) {
      LibraryEmptyMessage(
        message = "${selectedSource?.label ?: "選択したサービス"} の蔵書はありません。",
        modifier = Modifier.weight(1f),
      )
    } else {
      LibraryBookGrid(
        books = sortedBooks,
        actionLabel = "非表示",
        onAction = onHideBook,
        onEditSeries = onEditSeries,
        modifier = Modifier.weight(1f),
      )
    }
  }
}

@Composable
private fun LibrarySeriesTab(
  books: List<LibraryBook>,
  hiddenCount: Int,
  selectedSource: LibrarySource?,
  onSelectedSourceChange: (LibrarySource?) -> Unit,
  expandedSeries: Set<String>,
  onExpandedSeriesChange: (Set<String>) -> Unit,
  onHideBook: (LibraryBook) -> Unit,
  onEditSeries: (LibraryBook) -> Unit,
) {
  if (books.isEmpty()) {
    LibraryEmptyMessage(
      if (hiddenCount > 0) {
        "表示中の蔵書はありません。非表示タブに $hiddenCount 冊あります。"
      } else {
        "蔵書がありません。設定から蔵書サービスを同期またはインポートしてください。"
      },
    )
    return
  }

  val filteredBooks = remember(books, selectedSource) {
    filterLibraryBooksBySource(books, selectedSource)
  }
  val groups = remember(filteredBooks) { groupLibraryBooks(filteredBooks) }

  Column(Modifier.fillMaxSize()) {
    LibrarySourceFilterBar(
      selectedSource = selectedSource,
      onSelectedSourceChange = onSelectedSourceChange,
    )
    if (filteredBooks.isEmpty()) {
      LibraryEmptyMessage(
        message = "${selectedSource?.label ?: "選択したサービス"} の蔵書はありません。",
        modifier = Modifier.weight(1f),
      )
    } else {
      LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 112.dp),
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        groups.series.forEach { section ->
          val expanded = section.key in expandedSeries
          item(
            key = "series:${section.key}",
            span = { GridItemSpan(maxLineSpan) },
          ) {
            LibrarySeriesHeader(
              name = section.name,
              count = section.books.size,
              coverBook = section.books.firstOrNull { !it.thumbnailUrl.isNullOrBlank() } ?: section.books.first(),
              expanded = expanded,
              onToggle = {
                onExpandedSeriesChange(
                  if (expanded) expandedSeries - section.key else expandedSeries + section.key,
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
  }
}

@Composable
private fun LibrarySourceFilterBar(
  selectedSource: LibrarySource?,
  onSelectedSourceChange: (LibrarySource?) -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .horizontalScroll(rememberScrollState())
      .padding(horizontal = 12.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    FilterChip(
      selected = selectedSource == null,
      onClick = { onSelectedSourceChange(null) },
      label = { Text("すべて") },
    )
    LibrarySource.entries.forEach { source ->
      FilterChip(
        selected = selectedSource == source,
        onClick = { onSelectedSourceChange(source) },
        label = { Text(source.label) },
      )
    }
  }
}

@Composable
private fun LibraryHiddenTab(
  books: List<LibraryBook>,
  selectedSource: LibrarySource?,
  onSelectedSourceChange: (LibrarySource?) -> Unit,
  onRestoreBook: (LibraryBook) -> Unit,
  onEditSeries: (LibraryBook) -> Unit,
) {
  val filteredBooks = remember(books, selectedSource) {
    filterLibraryBooksBySource(books, selectedSource)
  }
  val sortedBooks = remember(filteredBooks) {
    filteredBooks.sortedWith(compareBy<LibraryBook> { it.title.lowercase() }.thenBy { it.sourceId })
  }

  Column(Modifier.fillMaxSize()) {
    LibrarySourceFilterBar(
      selectedSource = selectedSource,
      onSelectedSourceChange = onSelectedSourceChange,
    )
    when {
      books.isEmpty() -> LibraryEmptyMessage(
        message = "非表示の蔵書はありません。",
        modifier = Modifier.weight(1f),
      )

      sortedBooks.isEmpty() -> LibraryEmptyMessage(
        message = "${selectedSource?.label ?: "選択したサービス"} の非表示の蔵書はありません。",
        modifier = Modifier.weight(1f),
      )

      else -> LibraryBookGrid(
        books = sortedBooks,
        actionLabel = "再表示",
        onAction = onRestoreBook,
        onEditSeries = onEditSeries,
        modifier = Modifier.weight(1f),
      )
    }
  }
}

@Composable
private fun LibrarySettingsTab(
  state: LibraryUiState,
  onSyncGooglePlayBooks: () -> Unit,
  onImportKindle: () -> Unit,
  onImportAudible: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState()),
  ) {
    LibrarySyncHeader(
      state = state,
      onSyncGooglePlayBooks = onSyncGooglePlayBooks,
      onImportKindle = onImportKindle,
      onImportAudible = onImportAudible,
    )
    HorizontalDivider()
    Column(
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
      KindleWebLibraryImportGuide()
    }
    HorizontalDivider()
    Column(
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
      AudibleWebLibraryImportGuide()
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
private fun LibraryEmptyMessage(
  message: String,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier.fillMaxSize(),
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
  onImportKindle: () -> Unit,
  onImportAudible: () -> Unit,
) {
  Column(
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    LibrarySourceActionRow(
      source = LibrarySource.GOOGLE_PLAY_BOOKS,
      state = state,
      actionLabel = "同期",
      busy = state.syncing,
      enabled = !state.syncing && state.importingSource == null,
      accountLabel = state.sourceStates[LibrarySource.GOOGLE_PLAY_BOOKS]?.accountLabel,
      onAction = onSyncGooglePlayBooks,
    )
    LibrarySourceActionRow(
      source = LibrarySource.KINDLE,
      state = state,
      actionLabel = "インポート",
      busy = state.importingSource == LibrarySource.KINDLE,
      enabled = !state.syncing && state.importingSource == null,
      onAction = onImportKindle,
    )
    LibrarySourceActionRow(
      source = LibrarySource.AUDIBLE,
      state = state,
      actionLabel = "インポート",
      busy = state.importingSource == LibrarySource.AUDIBLE,
      enabled = !state.syncing && state.importingSource == null,
      onAction = onImportAudible,
    )
    Text(
      "Kindle / Audible は下のブックマークレットで作成した JSON を端末内で読み込みます。Amazon / Audible の認証情報は保存しません。",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun LibrarySourceActionRow(
  source: LibrarySource,
  state: LibraryUiState,
  actionLabel: String,
  busy: Boolean,
  enabled: Boolean,
  accountLabel: String? = null,
  onAction: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Column(Modifier.weight(1f)) {
      Text(source.label, style = MaterialTheme.typography.titleMedium)
      Text(
        state.sourceStates[source]?.lastSyncedAtEpochMillis?.let(::formatSyncTime) ?: "未同期",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      accountLabel?.let { account ->
        Text(
          account,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    Button(onClick = onAction, enabled = enabled) {
      if (busy) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
      } else {
        Text(actionLabel)
      }
    }
  }
}

@Composable
private fun LibrarySeriesHeader(
  name: String,
  count: Int,
  coverBook: LibraryBook,
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
        .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Card(
        modifier = Modifier.size(width = 52.dp, height = 76.dp),
      ) {
        LibraryBookCover(
          book = coverBook,
          modifier = Modifier.fillMaxSize(),
        )
      }
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
  var actionMenuExpanded by remember(book.source, book.sourceId) { mutableStateOf(false) }
  val openUrl = remember(book.source, book.sourceId, book.infoUrl) { book.openUrl() }
  val hasInfoUrl = openUrl != null

  Box(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .aspectRatio(0.68f)
          .combinedClickable(
            onClickLabel = if (hasInfoUrl) "書籍を開く" else "操作メニュー",
            onLongClickLabel = "操作メニュー",
            onLongClick = { actionMenuExpanded = true },
            onClick = {
              if (openUrl != null) {
                uriHandler.openUri(openUrl)
              } else {
                actionMenuExpanded = true
              }
            },
          ),
      ) {
        LibraryBookCover(
          book = book,
          modifier = Modifier.fillMaxSize(),
        )
      }

      Spacer(Modifier.height(6.dp))
      Text(
        book.source.label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
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
      if (book.source == LibrarySource.AUDIBLE && book.narrators.isNotEmpty()) {
        Text(
          "ナレーター: ${book.narrators.joinToString(", ")}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      if (book.source == LibrarySource.AUDIBLE) {
        val audibleDetails = listOfNotNull(
          book.duration?.takeIf(String::isNotBlank)?.let { "再生 $it" },
          book.publishedDate?.takeIf(String::isNotBlank)?.let { "配信 $it" },
        ).joinToString(" / ")
        if (audibleDetails.isNotEmpty()) {
          Text(
            audibleDetails,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }

    DropdownMenu(
      expanded = actionMenuExpanded,
      onDismissRequest = { actionMenuExpanded = false },
    ) {
      DropdownMenuItem(
        text = { Text("シリーズを編集") },
        onClick = {
          actionMenuExpanded = false
          onEditSeries()
        },
      )
      DropdownMenuItem(
        text = { Text(actionLabel) },
        onClick = {
          actionMenuExpanded = false
          onAction()
        },
      )
    }
  }
}

@Composable
private fun LibraryBookCover(
  book: LibraryBook,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
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
          onValueChange = { value -> positionText = value.filter(Char::isDigit) },
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

private fun LibraryBook.openUrl(): String? {
  infoUrl?.trim()?.takeIf(String::isNotEmpty)?.let { return it }
  if (source != LibrarySource.AUDIBLE) return null

  val asin = sourceId.trim().uppercase(Locale.ROOT)
  if (!AUDIBLE_ASIN.matches(asin)) return null
  return "https://www.audible.co.jp/pd/$asin"
}

private val AUDIBLE_ASIN = Regex("^[A-Z0-9]{10}$")

private fun formatSyncTime(epochMillis: Long): String {
  val formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
  val local = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
  return "最終更新: ${formatter.format(local)}"
}
