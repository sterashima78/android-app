package dev.terashima.yomitorirss.feature.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private enum class MetadataManagementSection(val label: String) {
  TAGS("タグ"),
  COLLECTIONS("コレクション"),
  SERIES("シリーズ"),
}

@Composable
fun LibraryMetadataManagementDialog(
  books: List<LibraryBook>,
  state: LibraryOrganizationUiState,
  onSave: (LibraryBook, LibraryOrganizationDraft) -> Unit,
  onSuggest: (LibraryBook) -> Unit,
  onStartBatch: (List<LibraryBook>) -> Unit,
  onPauseBatch: () -> Unit,
  onResumeBatch: () -> Unit,
  onReorganizeSeries: (List<LibraryBook>) -> Unit,
  onDismissMessage: () -> Unit,
  onDismiss: () -> Unit,
) {
  var sectionName by rememberSaveable { mutableStateOf(MetadataManagementSection.TAGS.name) }
  var showAiOrganizer by rememberSaveable { mutableStateOf(false) }
  val section = MetadataManagementSection.valueOf(sectionName)
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(state.message, showAiOrganizer) {
    val message = state.message ?: return@LaunchedEffect
    if (!showAiOrganizer) {
      snackbarHostState.showSnackbar(message)
      onDismissMessage()
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(
      usePlatformDefaultWidth = false,
      decorFitsSystemWindows = false,
    ),
  ) {
    Surface(Modifier.fillMaxSize()) {
      Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(Modifier.weight(1f)) {
              Text(
                "蔵書のメタ情報を整理",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
              )
              Text(
                "タグ・コレクションから蔵書を外したり、シリーズ単位でAI整理をやり直せます。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            TextButton(onClick = onDismiss) { Text("閉じる") }
          }

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState())
              .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            MetadataManagementSection.entries.forEach { candidate ->
              FilterChip(
                selected = candidate == section,
                onClick = { sectionName = candidate.name },
                label = { Text(candidate.label) },
              )
            }
            Button(onClick = { showAiOrganizer = true }) {
              Text("AI一括整理")
            }
          }
          HorizontalDivider()

          when {
            !state.initialized || state.loading -> LoadingMetadataManagement()
            section == MetadataManagementSection.TAGS -> TagManagementContent(
              books = books,
              state = state,
              onSave = onSave,
            )
            section == MetadataManagementSection.COLLECTIONS -> CollectionManagementContent(
              books = books,
              state = state,
              onSave = onSave,
            )
            else -> SeriesManagementContent(
              books = books,
              state = state,
              onReorganizeSeries = onReorganizeSeries,
            )
          }
        }
      }
    }
  }

  if (showAiOrganizer) {
    LibraryOrganizationDialog(
      books = books,
      state = state,
      onSave = onSave,
      onSuggest = onSuggest,
      onStartBatch = onStartBatch,
      onPauseBatch = onPauseBatch,
      onResumeBatch = onResumeBatch,
      onDismissMessage = onDismissMessage,
      onDismiss = { showAiOrganizer = false },
    )
  }
}

@Composable
private fun LoadingMetadataManagement() {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    CircularProgressIndicator()
  }
}

@Composable
private fun TagManagementContent(
  books: List<LibraryBook>,
  state: LibraryOrganizationUiState,
  onSave: (LibraryBook, LibraryOrganizationDraft) -> Unit,
) {
  val groups = remember(books, state.snapshot) {
    state.snapshot.tags.mapNotNull { tag ->
      val members = books.filter { book ->
        state.snapshot.organizationFor(book).tags.any { it.id == tag.id }
      }
      members.takeIf(List<LibraryBook>::isNotEmpty)?.let { tag to it }
    }.sortedBy { (tag, _) -> tag.name.lowercase() }
  }
  MetadataGroupList(
    emptyMessage = "書籍に設定されているタグはありません。",
    groups = groups.map { (tag, members) -> MetadataBookGroup(tag.id, tag.name, members) },
    state = state,
    onRemove = { book, groupId ->
      val organization = state.snapshot.organizationFor(book)
      onSave(
        book,
        LibraryOrganizationDraft(
          tagNames = organization.tags.filterNot { it.id == groupId }.map(LibraryOrganizationTag::name),
          collectionNames = organization.collections.map(LibraryCollection::name),
          readingStatus = organization.readingStatus,
        ),
      )
    },
  )
}

@Composable
private fun CollectionManagementContent(
  books: List<LibraryBook>,
  state: LibraryOrganizationUiState,
  onSave: (LibraryBook, LibraryOrganizationDraft) -> Unit,
) {
  val groups = remember(books, state.snapshot) {
    state.snapshot.collections.mapNotNull { collection ->
      val members = books.filter { book ->
        state.snapshot.organizationFor(book).collections.any { it.id == collection.id }
      }
      members.takeIf(List<LibraryBook>::isNotEmpty)?.let { collection to it }
    }.sortedBy { (collection, _) -> collection.name.lowercase() }
  }
  MetadataGroupList(
    emptyMessage = "書籍に設定されているコレクションはありません。",
    groups = groups.map { (collection, members) ->
      MetadataBookGroup(collection.id, collection.name, members)
    },
    state = state,
    onRemove = { book, groupId ->
      val organization = state.snapshot.organizationFor(book)
      onSave(
        book,
        LibraryOrganizationDraft(
          tagNames = organization.tags.map(LibraryOrganizationTag::name),
          collectionNames = organization.collections
            .filterNot { it.id == groupId }
            .map(LibraryCollection::name),
          readingStatus = organization.readingStatus,
        ),
      )
    },
  )
}

private data class MetadataBookGroup(
  val id: String,
  val name: String,
  val books: List<LibraryBook>,
)

@Composable
private fun MetadataGroupList(
  emptyMessage: String,
  groups: List<MetadataBookGroup>,
  state: LibraryOrganizationUiState,
  onRemove: (LibraryBook, String) -> Unit,
) {
  if (groups.isEmpty()) {
    Text(
      emptyMessage,
      modifier = Modifier.padding(24.dp),
      style = MaterialTheme.typography.bodyMedium,
    )
    return
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    item { Spacer(Modifier.height(2.dp)) }
    items(groups, key = MetadataBookGroup::id) { group ->
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp),
      ) {
        Column(
          modifier = Modifier.padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            "${group.name} · ${group.books.size} 冊",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
          )
          group.books.forEach { book ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Column(Modifier.weight(1f)) {
                Text(
                  book.title,
                  style = MaterialTheme.typography.bodyMedium,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  book.source.label,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              TextButton(
                enabled = state.savingBook == null && state.reorganizingSeriesBook == null,
                onClick = { onRemove(book, group.id) },
              ) {
                Text(if (state.savingBook == book.organizationKey()) "保存中" else "外す")
              }
            }
          }
        }
      }
    }
    item { Spacer(Modifier.height(24.dp)) }
  }
}

@Composable
private fun SeriesManagementContent(
  books: List<LibraryBook>,
  state: LibraryOrganizationUiState,
  onReorganizeSeries: (List<LibraryBook>) -> Unit,
) {
  val seriesGroups = remember(books) { groupLibraryBooks(books).series }
  var pendingSeries by remember { mutableStateOf<LibrarySeriesSection?>(null) }
  if (seriesGroups.isEmpty()) {
    Text(
      "シリーズ情報が設定された蔵書はありません。",
      modifier = Modifier.padding(24.dp),
      style = MaterialTheme.typography.bodyMedium,
    )
    return
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    item {
      Text(
        "再整理では現在のメタ情報を保持し、AI解析に成功した書籍だけ新しいタグ・コレクションへ置き換えます。",
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    items(seriesGroups, key = LibrarySeriesSection::key) { group ->
      val reorganizingSeriesBook = state.reorganizingSeriesBook
      val running = reorganizingSeriesBook != null &&
        group.books.any { it.organizationKey() == reorganizingSeriesBook }
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp),
      ) {
        Column(
          modifier = Modifier.padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            group.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            "${group.books.size} 冊",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          group.books.take(4).forEach { book ->
            Text(
              book.title,
              style = MaterialTheme.typography.bodySmall,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          if (group.books.size > 4) {
            Text(
              "ほか ${group.books.size - 4} 冊",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Button(
            enabled = state.reorganizingSeriesBook == null &&
              state.savingBook == null &&
              state.batch?.status != LibraryOrganizationBatchStatus.RUNNING,
            onClick = { pendingSeries = group },
          ) {
            if (running) {
              CircularProgressIndicator(
                modifier = Modifier.height(18.dp),
                strokeWidth = 2.dp,
              )
            } else {
              Text("このシリーズをAIで再整理")
            }
          }
        }
      }
    }
    item { Spacer(Modifier.height(24.dp)) }
  }

  pendingSeries?.let { group ->
    AlertDialog(
      onDismissRequest = { pendingSeries = null },
      title = { Text("シリーズを再整理しますか？") },
      text = {
        Text(
          "「${group.name}」${group.books.size} 冊のタグ・コレクションをAIで作り直します。解析に失敗した書籍は現在の情報を保持します。",
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            pendingSeries = null
            onReorganizeSeries(group.books)
          },
        ) {
          Text("再整理")
        }
      },
      dismissButton = {
        TextButton(onClick = { pendingSeries = null }) { Text("キャンセル") }
      },
    )
  }
}
