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
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class LibraryTab(val label: String) {
  ALL("全体"),
  SERIES("シリーズ"),
  HIDDEN("非表示"),
  SETTINGS("設定"),
}

internal sealed interface LibraryBookTapAction {
  data object OpenSmbBook : LibraryBookTapAction

  data class OpenExternalUri(val uri: String) : LibraryBookTapAction

  data object OpenMenu : LibraryBookTapAction
}

internal fun LibraryBook.tapAction(): LibraryBookTapAction = when {
  source == LibrarySource.SMB -> LibraryBookTapAction.OpenSmbBook
  else -> openUrl()?.let(LibraryBookTapAction::OpenExternalUri) ?: LibraryBookTapAction.OpenMenu
}

@Composable
fun LibraryScreen(
  state: LibraryUiState,
  onSyncGooglePlayBooks: () -> Unit,
  onOpenSmbBook: (LibraryBook) -> Unit,
  onHideBook: (LibraryBook) -> Unit,
  onRestoreBook: (LibraryBook) -> Unit,
  onSetBookSeries: (LibraryBook, String, Int?) -> Unit,
  onMergeSeries: (List<LibraryBookSeriesUpdate>) -> Unit,
  onClearBookSeries: (LibraryBook) -> Unit,
  onOpenOrganization: () -> Unit,
  onDismissMessage: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val snackbarHostState = remember { SnackbarHostState() }
  var selectedTabName by rememberSaveable { mutableStateOf(LibraryTab.ALL.name) }
  var selectedSourceName by rememberSaveable { mutableStateOf<String?>(null) }
  var searchQuery by rememberSaveable { mutableStateOf("") }
  var seriesEditorBook by remember { mutableStateOf<LibraryBook?>(null) }
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
          if (tab == LibraryTab.SETTINGS) {
            NavigationBarItem(
              selected = false,
              onClick = onOpenOrganization,
              icon = {
                Icon(
                  imageVector = Icons.Default.Edit,
                  contentDescription = "整理",
                )
              },
              label = { Text("整理", maxLines = 1) },
            )
          }
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
            searchQuery = searchQuery,
            onSelectedSourceChange = { selectedSourceName = it?.name },
            onSearchQueryChange = { searchQuery = it },
            onOpenSmbBook = onOpenSmbBook,
            onHideBook = onHideBook,
            onEditSeries = { seriesEditorBook = it },
          )

          LibraryTab.SERIES -> LibrarySeriesTab(
            books = state.books,
            hiddenCount = state.hiddenBooks.size,
            selectedSource = selectedSource,
            searchQuery = searchQuery,
            onSelectedSourceChange = { selectedSourceName = it?.name },
            onSearchQueryChange = { searchQuery = it },
            onOpenSmbBook = onOpenSmbBook,
            onHideBook = onHideBook,
            onEditSeries = { seriesEditorBook = it },
            onMergeSeries = onMergeSeries,
          )

          LibraryTab.HIDDEN -> LibraryHiddenTab(
            books = state.hiddenBooks,
            selectedSource = selectedSource,
            searchQuery = searchQuery,
            onSelectedSourceChange = { selectedSourceName = it?.name },
            onSearchQueryChange = { searchQuery = it },
            onOpenSmbBook = onOpenSmbBook,
            onRestoreBook = onRestoreBook,
            onEditSeries = { seriesEditorBook = it },
          )

          LibraryTab.SETTINGS -> LibrarySettingsTab(
            state = state,
            onSyncGooglePlayBooks = onSyncGooglePlayBooks,
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
  searchQuery: String,
  onSelectedSourceChange: (LibrarySource?) -> Unit,
  onSearchQueryChange: (String) -> Unit,
  onOpenSmbBook: (LibraryBook) -> Unit,
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

  val filteredBooks = remember(books, selectedSource, searchQuery) {
    filterLibraryBooksByText(
      filterLibraryBooksBySource(books, selectedSource),
      searchQuery,
    )
  }
  val sortedBooks = remember(filteredBooks) {
    filteredBooks.sortedWith(compareBy<LibraryBook> { it.title.lowercase() }.thenBy { it.sourceId })
  }

  Column(Modifier.fillMaxSize()) {
    LibrarySearchField(
      query = searchQuery,
      onQueryChange = onSearchQueryChange,
    )
    LibrarySourceFilterBar(
      selectedSource = selectedSource,
      onSelectedSourceChange = onSelectedSourceChange,
    )
    if (sortedBooks.isEmpty()) {
      LibraryEmptyMessage(
        message = if (searchQuery.isNotBlank()) {
          "「${searchQuery.trim()}」に一致する蔵書はありません。"
        } else {
          "${selectedSource?.label ?: "選択したサービス"} の蔵書はありません。"
        },
        modifier = Modifier.weight(1f),
      )
    } else {
      LibraryBookGrid(
        books = sortedBooks,
        actionLabel = "非表示",
        onOpenSmbBook = onOpenSmbBook,
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
  searchQuery: String,
  onSelectedSourceChange: (LibrarySource?) -> Unit,
  onSearchQueryChange: (String) -> Unit,
  onOpenSmbBook: (LibraryBook) -> Unit,
  onHideBook: (LibraryBook) -> Unit,
  onEditSeries: (LibraryBook) -> Unit,
  onMergeSeries: (List<LibraryBookSeriesUpdate>) -> Unit,
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

  var selectedSeriesKey by rememberSaveable { mutableStateOf<String?>(null) }
  val filteredBooks = remember(books, selectedSource, searchQuery) {
    filterLibraryBooksByText(
      filterLibraryBooksBySource(books, selectedSource),
      searchQuery,
    )
  }
  val groups = remember(filteredBooks) { groupLibraryBooks(filteredBooks) }
  val allGroups = remember(books) { groupLibraryBooks(books) }
  val selectedSeries = groups.series.firstOrNull { it.key == selectedSeriesKey }

  selectedSeries?.let { filteredSection ->
    val section = allGroups.series.firstOrNull { it.key == filteredSection.key } ?: filteredSection
    LibrarySeriesBooksSheet(
      section = section,
      mergeTargets = allGroups.series.filter { it.key != section.key },
      onDismiss = { selectedSeriesKey = null },
      onOpenSmbBook = onOpenSmbBook,
      onHideBook = onHideBook,
      onEditSeries = { book ->
        selectedSeriesKey = null
        onEditSeries(book)
      },
      onMerge = { target ->
        selectedSeriesKey = null
        onMergeSeries(mergeLibrarySeries(section, target))
      },
    )
  }

  Column(Modifier.fillMaxSize()) {
    LibrarySearchField(
      query = searchQuery,
      onQueryChange = { query ->
        selectedSeriesKey = null
        onSearchQueryChange(query)
      },
    )
    LibrarySourceFilterBar(
      selectedSource = selectedSource,
      onSelectedSourceChange = { source ->
        selectedSeriesKey = null
        onSelectedSourceChange(source)
      },
    )
    if (filteredBooks.isEmpty()) {
      LibraryEmptyMessage(
        message = if (searchQuery.isNotBlank()) {
          "「${searchQuery.trim()}」に一致する蔵書はありません。"
        } else {
          "${selectedSource?.label ?: "選択したサービス"} の蔵書はありません。"
        },
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
          item(
            key = "series:${section.key}",
          ) {
            LibrarySeriesThumbnail(
              name = section.name,
              count = section.books.size,
              coverBook = section.books.firstOrNull { !it.thumbnailUrl.isNullOrBlank() } ?: section.books.first(),
              onClick = { selectedSeriesKey = section.key },
            )
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
              onOpenSmbBook = { onOpenSmbBook(book) },
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
private fun LibrarySeriesBooksSheet(
  section: LibrarySeriesSection,
  mergeTargets: List<LibrarySeriesSection>,
  onDismiss: () -> Unit,
  onOpenSmbBook: (LibraryBook) -> Unit,
  onHideBook: (LibraryBook) -> Unit,
  onEditSeries: (LibraryBook) -> Unit,
  onMerge: (LibrarySeriesSection) -> Unit,
) {
  var mergeMenuExpanded by remember(section.key) { mutableStateOf(false) }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(
      usePlatformDefaultWidth = false,
      dismissOnClickOutside = false,
    ),
  ) {
    Surface(
      modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing),
      color = MaterialTheme.colorScheme.surface,
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
          verticalAlignment = Alignment.Top,
        ) {
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            Text(
              section.name,
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.SemiBold,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              "${section.books.size} 冊",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (mergeTargets.isNotEmpty()) {
              Box {
                TextButton(onClick = { mergeMenuExpanded = true }) {
                  Text("別のシリーズにマージ")
                }
                DropdownMenu(
                  expanded = mergeMenuExpanded,
                  onDismissRequest = { mergeMenuExpanded = false },
                ) {
                  mergeTargets.forEach { target ->
                    DropdownMenuItem(
                      text = {
                        Column {
                          Text(target.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                          Text(
                            target.books.map { it.source.label }.distinct().joinToString(" / "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                          )
                        }
                      },
                      onClick = {
                        mergeMenuExpanded = false
                        onMerge(target)
                      },
                    )
                  }
                }
              }
            }
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "閉じる")
          }
        }
        LibraryBookGrid(
          books = section.books,
          actionLabel = "非表示",
          onOpenSmbBook = onOpenSmbBook,
          onAction = onHideBook,
          onEditSeries = onEditSeries,
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@Composable
private fun LibrarySearchField(
  query: String,
  onQueryChange: (String) -> Unit,
) {
  OutlinedTextField(
    value = query,
    onValueChange = onQueryChange,
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 12.dp, vertical = 8.dp),
    label = { Text("蔵書を検索") },
    placeholder = { Text("タイトル・著者・シリーズなど") },
    singleLine = true,
  )
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
  searchQuery: String,
  onSelectedSourceChange: (LibrarySource?) -> Unit,
  onSearchQueryChange: (String) -> Unit,
  onOpenSmbBook: (LibraryBook) -> Unit,
  onRestoreBook: (LibraryBook) -> Unit,
  onEditSeries: (LibraryBook) -> Unit,
) {
  val filteredBooks = remember(books, selectedSource, searchQuery) {
    filterLibraryBooksByText(
      filterLibraryBooksBySource(books, selectedSource),
      searchQuery,
    )
  }
  val sortedBooks = remember(filteredBooks) {
    filteredBooks.sortedWith(compareBy<LibraryBook> { it.title.lowercase() }.thenBy { it.sourceId })
  }

  Column(Modifier.fillMaxSize()) {
    LibrarySearchField(
      query = searchQuery,
      onQueryChange = onSearchQueryChange,
    )
    LibrarySourceFilterBar(
      selectedSource = selectedSource,
      onSelectedSourceChange = onSelectedSourceChange,
    )
    when {
      books.isEmpty() -> LibraryEmptyMessage(
        message = "非表示の蔵書はありません。",
        modifier = Modifier.weight(1f),
      )

      sortedBooks.isEmpty() && searchQuery.isNotBlank() -> LibraryEmptyMessage(
        message = "「${searchQuery.trim()}」に一致する非表示の蔵書はありません。",
        modifier = Modifier.weight(1f),
      )

      sortedBooks.isEmpty() -> LibraryEmptyMessage(
        message = "${selectedSource?.label ?: "選択したサービス"} の非表示の蔵書はありません。",
        modifier = Modifier.weight(1f),
      )

      else -> LibraryBookGrid(
        books = sortedBooks,
        actionLabel = "再表示",
        onOpenSmbBook = onOpenSmbBook,
        onAction = onRestoreBook,
        onEditSeries = onEditSeries,
        allowWebMetadataRefresh = false,
        modifier = Modifier.weight(1f),
      )
    }
  }
}

@Composable
private fun LibrarySettingsTab(
  state: LibraryUiState,
  onSyncGooglePlayBooks: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState()),
  ) {
    LibrarySyncHeader(
      state = state,
      onSyncGooglePlayBooks = onSyncGooglePlayBooks,
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
  onOpenSmbBook: (LibraryBook) -> Unit,
  onAction: (LibraryBook) -> Unit,
  onEditSeries: (LibraryBook) -> Unit,
  modifier: Modifier = Modifier,
  allowWebMetadataRefresh: Boolean = true,
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
        onOpenSmbBook = { onOpenSmbBook(book) },
        onAction = { onAction(book) },
        onEditSeries = { onEditSeries(book) },
        allowWebMetadataRefresh = allowWebMetadataRefresh,
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
    Text(
      "Kindle / Audible は下の専用 WebView から取り込みます。ファイルインポートには対応しません。Amazon / Audible の認証情報は保存しません。",
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

private fun formatSyncTime(epochMillis: Long): String {
  val formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
  val local = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
  return "最終更新: ${formatter.format(local)}"
}
