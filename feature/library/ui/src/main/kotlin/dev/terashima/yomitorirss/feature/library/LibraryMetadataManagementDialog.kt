package dev.terashima.yomitorirss.feature.library

import androidx.compose.foundation.clickable
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
  var selectedGroupId by rememberSaveable { mutableStateOf<String?>(null) }
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
    onDismissRequest = {
      if (selectedGroupId != null) selectedGroupId = null else onDismiss()
    },
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
                "蔵書の分類を管理",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
              )
              Text(
                "タグやコレクションごとに蔵書を閲覧し、所属を簡単に調整できます。",
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
                onClick = {
                  sectionName = candidate.name
                  selectedGroupId = null
                },
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
              selectedGroupId = selectedGroupId,
              onSelectGroup = { selectedGroupId = it },
              onSave = onSave,
            )
            section == MetadataManagementSection.COLLECTIONS -> CollectionManagementContent(
              books = books,
              state = state,
              selectedGroupId = selectedGroupId,
              onSelectGroup = { selectedGroupId = it },
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
  selectedGroupId: String?,
  onSelectGroup: (String?) -> Unit,
  onSave: (LibraryBook, LibraryOrganizationDraft) -> Unit,
) {
  val groups = remember(books, state.snapshot) {
    libraryTagGroups(books, state.snapshot)
  }
  MetadataGroupBrowser(
    groupTypeLabel = "タグ",
    emptyMessage = "書籍に設定されているタグはありません。",
    groups = groups,
    state = state,
    selectedGroupId = selectedGroupId,
    onSelectGroup = onSelectGroup,
    removeLabel = "タグを外す",
    onRemove = { book, groupId ->
      onSave(book, state.snapshot.organizationFor(book).withoutTag(groupId))
    },
  )
}

@Composable
private fun CollectionManagementContent(
  books: List<LibraryBook>,
  state: LibraryOrganizationUiState,
  selectedGroupId: String?,
  onSelectGroup: (String?) -> Unit,
  onSave: (LibraryBook, LibraryOrganizationDraft) -> Unit,
) {
  val groups = remember(books, state.snapshot) {
    libraryCollectionGroups(books, state.snapshot)
  }
  MetadataGroupBrowser(
    groupTypeLabel = "コレクション",
    emptyMessage = "書籍に設定されているコレクションはありません。",
    groups = groups,
    state = state,
    selectedGroupId = selectedGroupId,
    onSelectGroup = onSelectGroup,
    removeLabel = "コレクションから外す",
    onRemove = { book, groupId ->
      onSave(book, state.snapshot.organizationFor(book).withoutCollection(groupId))
    },
  )
}

@Composable
private fun MetadataGroupBrowser(
  groupTypeLabel: String,
  emptyMessage: String,
  groups: List<LibraryMetadataBookGroup>,
  state: LibraryOrganizationUiState,
  selectedGroupId: String?,
  onSelectGroup: (String?) -> Unit,
  removeLabel: String,
  onRemove: (LibraryBook, String) -> Unit,
) {
  val selectedGroup = groups.firstOrNull { it.id == selectedGroupId }

  LaunchedEffect(groups, selectedGroupId) {
    if (selectedGroupId != null && selectedGroup == null) onSelectGroup(null)
  }

  if (selectedGroup != null) {
    MetadataGroupDetail(
      groupTypeLabel = groupTypeLabel,
      group = selectedGroup,
      state = state,
      removeLabel = removeLabel,
      onBack = { onSelectGroup(null) },
      onRemove = onRemove,
    )
    return
  }

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
    item {
      Text(
        "$groupTypeLabelを選ぶと、所属する蔵書をまとめて閲覧・編集できます。",
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    items(groups, key = LibraryMetadataBookGroup::id) { group ->
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp)
          .clickable { onSelectGroup(group.id) },
      ) {
        Column(
          modifier = Modifier.padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              group.name,
              modifier = Modifier.weight(1f),
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              "${group.books.size} 冊",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          group.books.take(GROUP_PREVIEW_BOOK_COUNT).forEach { book ->
            Text(
              book.title,
              style = MaterialTheme.typography.bodySmall,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          if (group.books.size > GROUP_PREVIEW_BOOK_COUNT) {
            Text(
              "ほか ${group.books.size - GROUP_PREVIEW_BOOK_COUNT} 冊",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Text(
            "閲覧・編集 →",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }
    }
    item { Spacer(Modifier.height(24.dp)) }
  }
}

@Composable
private fun MetadataGroupDetail(
  groupTypeLabel: String,
  group: LibraryMetadataBookGroup,
  state: LibraryOrganizationUiState,
  removeLabel: String,
  onBack: () -> Unit,
  onRemove: (LibraryBook, String) -> Unit,
) {
  Column(Modifier.fillMaxSize()) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      TextButton(onClick = onBack) {
        Text("← ${groupTypeLabel}一覧")
      }
      Column(
        modifier = Modifier
          .weight(1f)
          .padding(horizontal = 8.dp),
      ) {
        Text(
          group.name,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          "${group.books.size} 冊",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    Text(
      "各蔵書の現在の分類を確認しながら、この$groupTypeLabelから直接外せます。",
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      item { Spacer(Modifier.height(4.dp)) }
      items(
        items = group.books,
        key = { "${it.source.name}:${it.sourceId}" },
      ) { book ->
        val organization = state.snapshot.organizationFor(book)
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        ) {
          Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              book.title,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Medium,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              book.source.label,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MetadataBookClassificationSummary(organization)
            TextButton(
              enabled = state.savingBook == null && state.reorganizingSeriesBook == null,
              onClick = { onRemove(book, group.id) },
            ) {
              Text(
                if (state.savingBook == book.organizationKey()) "保存中" else removeLabel,
              )
            }
          }
        }
      }
      item { Spacer(Modifier.height(24.dp)) }
    }
  }
}

@Composable
private fun MetadataBookClassificationSummary(organization: LibraryItemOrganization) {
  val collections = organization.collections.joinToString { it.name }
  val tags = organization.tags.joinToString { it.name }
  Text(
    if (collections.isEmpty()) "コレクション: 未設定" else "コレクション: $collections",
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines = 2,
    overflow = TextOverflow.Ellipsis,
  )
  Text(
    if (tags.isEmpty()) "タグ: 未設定" else "タグ: $tags",
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines = 2,
    overflow = TextOverflow.Ellipsis,
  )
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

private const val GROUP_PREVIEW_BOOK_COUNT = 3
