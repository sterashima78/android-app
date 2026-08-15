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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun LibraryOrganizationDialog(
  books: List<LibraryBook>,
  state: LibraryOrganizationUiState,
  onSave: (LibraryBook, LibraryOrganizationDraft) -> Unit,
  onSuggest: (LibraryBook) -> Unit,
  onStartBatchSuggest: (List<LibraryBook>) -> Unit,
  onCancelBatchSuggest: () -> Unit,
  onToggleBatchSelection: (LibraryBookKey) -> Unit,
  onSelectAllBatchCandidates: () -> Unit,
  onClearBatchSelection: () -> Unit,
  onUpdateBatchDraft: (LibraryBookKey, LibraryOrganizationBatchDraft) -> Unit,
  onApplyBatch: (List<LibraryBook>) -> Unit,
  onClearBatchReview: () -> Unit,
  onDismissMessage: () -> Unit,
  onDismiss: () -> Unit,
) {
  var selectedFilterName by rememberSaveable { mutableStateOf(LibraryOrganizationFilter.UNORGANIZED.name) }
  var editingBook by remember { mutableStateOf<LibraryBook?>(null) }
  var batchReviewVisible by rememberSaveable { mutableStateOf(false) }
  val selectedFilter = LibraryOrganizationFilter.valueOf(selectedFilterName)
  val filteredBooks = remember(books, state.snapshot, selectedFilter) {
    filterLibraryBooksForOrganization(books, state.snapshot, selectedFilter)
      .sortedWith(compareBy<LibraryBook> { it.title.lowercase() }.thenBy { it.sourceId })
  }
  val unorganizedCount = remember(books, state.snapshot) {
    filterLibraryBooksForOrganization(
      books,
      state.snapshot,
      LibraryOrganizationFilter.UNORGANIZED,
    ).size
  }
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(state.message) {
    val message = state.message ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(message)
    onDismissMessage()
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(
      usePlatformDefaultWidth = false,
      decorFitsSystemWindows = false,
    ),
  ) {
    Surface(Modifier.fillMaxSize()) {
      Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
      ) { padding ->
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
                "蔵書を整理",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
              )
              Text(
                "未整理 $unorganizedCount 冊 / 全 ${books.size} 冊",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            TextButton(onClick = onDismiss) { Text("閉じる") }
          }
          Text(
            "コレクション・タグ・読書状態を設定できます。AIは候補だけを作り、保存するまで蔵書を変更しません。",
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          LibraryBatchOrganizationCard(
            books = books,
            ready = state.initialized && !state.loading,
            unorganizedCount = unorganizedCount,
            batch = state.batch,
            onStart = {
              batchReviewVisible = true
              onStartBatchSuggest(books)
            },
            onReview = { batchReviewVisible = true },
            onCancel = onCancelBatchSuggest,
          )
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState())
              .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            LibraryOrganizationFilter.entries.forEach { filter ->
              val count = remember(books, state.snapshot, filter) {
                filterLibraryBooksForOrganization(books, state.snapshot, filter).size
              }
              FilterChip(
                selected = filter == selectedFilter,
                onClick = { selectedFilterName = filter.name },
                label = { Text("${filter.label} $count") },
              )
            }
          }
          HorizontalDivider()

          when {
            !state.initialized || state.loading -> {
              Column(
                modifier = Modifier
                  .fillMaxSize()
                  .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                CircularProgressIndicator()
              }
            }

            filteredBooks.isEmpty() -> {
              Text(
                "この条件に一致する蔵書はありません。",
                modifier = Modifier.padding(24.dp),
                style = MaterialTheme.typography.bodyMedium,
              )
            }

            else -> {
              LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                item { Spacer(Modifier.height(4.dp)) }
                items(
                  items = filteredBooks,
                  key = { "${it.source.name}:${it.sourceId}" },
                ) { book ->
                  LibraryOrganizationBookRow(
                    book = book,
                    organization = state.snapshot.organizationFor(book),
                    hasAiSuggestion = book.organizationKey() in state.suggestions,
                    onClick = { editingBook = book },
                  )
                }
                item { Spacer(Modifier.height(24.dp)) }
              }
            }
          }
        }
      }
    }
  }

  editingBook?.let { book ->
    LibraryOrganizationEditorDialog(
      book = book,
      organization = state.snapshot.organizationFor(book),
      existingTags = state.snapshot.tags.map(LibraryOrganizationTag::name),
      existingCollections = state.snapshot.collections.map(LibraryCollection::name),
      suggestion = state.suggestions[book.organizationKey()],
      saving = state.savingBook == book.organizationKey(),
      suggesting = state.suggestingBook == book.organizationKey(),
      aiEnabled = !state.batch.running && !state.batch.applying,
      onSuggest = { onSuggest(book) },
      onSave = { draft ->
        onSave(book, draft)
        editingBook = null
      },
      onDismiss = { editingBook = null },
    )
  }

  if (batchReviewVisible) {
    LibraryOrganizationBatchReviewDialog(
      books = books,
      batch = state.batch,
      suggestions = state.suggestions,
      onToggleSelection = onToggleBatchSelection,
      onSelectAll = onSelectAllBatchCandidates,
      onClearSelection = onClearBatchSelection,
      onUpdateDraft = onUpdateBatchDraft,
      onApply = { onApplyBatch(books) },
      onCancelGeneration = onCancelBatchSuggest,
      onClearReview = {
        onClearBatchReview()
        batchReviewVisible = false
      },
      onDismiss = { batchReviewVisible = false },
    )
  }
}

@Composable
private fun LibraryBatchOrganizationCard(
  books: List<LibraryBook>,
  ready: Boolean,
  unorganizedCount: Int,
  batch: LibraryOrganizationBatchUiState,
  onStart: () -> Unit,
  onReview: () -> Unit,
  onCancel: () -> Unit,
) {
  val booksByKey = remember(books) { books.associateBy(LibraryBook::organizationKey) }
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 12.dp, vertical = 10.dp),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text("AIでまとめて整理", style = MaterialTheme.typography.titleSmall)
      when {
        batch.running -> {
          Text(
            "${batch.completed} / ${batch.total} 冊を解析済み",
            style = MaterialTheme.typography.bodyMedium,
          )
          batch.currentBook?.let { key ->
            val title = booksByKey[key]?.title ?: "蔵書を解析中"
            Text(
              title,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onReview) { Text("候補をレビュー") }
            TextButton(onClick = onCancel) { Text("停止") }
          }
        }

        batch.drafts.isNotEmpty() || batch.failures.isNotEmpty() -> {
          Text(
            "候補 ${batch.drafts.size} 冊 / 失敗 ${batch.failures.size} 冊",
            style = MaterialTheme.typography.bodyMedium,
          )
          Button(onClick = onReview) { Text("候補をレビュー") }
        }

        else -> {
          Text(
            if (ready) {
              "未整理の蔵書を1冊ずつローカルAIで解析し、候補を確認してからまとめて適用します。"
            } else {
              "整理情報を読み込み中です。完了後に一括AI解析を開始できます。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Button(
            onClick = onStart,
            enabled = ready && unorganizedCount > 0 && !batch.applying,
          ) {
            Text(
              when {
                !ready -> "整理情報を読み込み中"
                unorganizedCount > 0 -> "未整理 $unorganizedCount 冊をAI解析"
                else -> "未整理の蔵書はありません"
              },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun LibraryOrganizationBatchReviewDialog(
  books: List<LibraryBook>,
  batch: LibraryOrganizationBatchUiState,
  suggestions: Map<LibraryBookKey, LibraryOrganizationSuggestion>,
  onToggleSelection: (LibraryBookKey) -> Unit,
  onSelectAll: () -> Unit,
  onClearSelection: () -> Unit,
  onUpdateDraft: (LibraryBookKey, LibraryOrganizationBatchDraft) -> Unit,
  onApply: () -> Unit,
  onCancelGeneration: () -> Unit,
  onClearReview: () -> Unit,
  onDismiss: () -> Unit,
) {
  val booksByKey = remember(books) { books.associateBy(LibraryBook::organizationKey) }
  val candidates = remember(booksByKey, batch.drafts) {
    batch.drafts.mapNotNull { (key, draft) ->
      booksByKey[key]?.let { book -> book to draft }
    }.sortedWith(compareBy<Pair<LibraryBook, LibraryOrganizationBatchDraft>> { it.first.title.lowercase() })
  }
  var editingCandidate by remember { mutableStateOf<LibraryBook?>(null) }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(
      usePlatformDefaultWidth = false,
      decorFitsSystemWindows = false,
    ),
  ) {
    Surface(Modifier.fillMaxSize()) {
      Column(Modifier.fillMaxSize()) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(Modifier.weight(1f)) {
            Text(
              "AI整理候補をレビュー",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              when {
                batch.running -> "${batch.completed} / ${batch.total} 冊を解析済み"
                batch.applying -> "選択した候補を保存しています"
                else -> "${batch.drafts.size} 件の候補 / ${batch.selectedKeys.size} 件を適用予定"
              },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          TextButton(onClick = onDismiss, enabled = !batch.applying) { Text("閉じる") }
        }

        if (batch.running) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            CircularProgressIndicator(modifier = Modifier.height(24.dp), strokeWidth = 2.dp)
            val title = batch.currentBook?.let(booksByKey::get)?.title
            Text(
              title ?: "次の蔵書を準備中",
              modifier = Modifier.weight(1f),
              style = MaterialTheme.typography.bodySmall,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = onCancelGeneration) { Text("停止") }
          }
        }

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          TextButton(
            onClick = onSelectAll,
            enabled = batch.drafts.isNotEmpty() && !batch.applying,
          ) { Text("すべて選択") }
          TextButton(
            onClick = onClearSelection,
            enabled = batch.selectedKeys.isNotEmpty() && !batch.applying,
          ) { Text("選択解除") }
          TextButton(
            onClick = onClearReview,
            enabled = !batch.running && !batch.applying,
          ) { Text("候補を破棄") }
        }
        HorizontalDivider()

        LazyColumn(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (candidates.isEmpty()) {
            item {
              Text(
                if (batch.running) "候補が生成されるとここに表示されます。" else "レビューできる候補はありません。",
                modifier = Modifier.padding(20.dp),
                style = MaterialTheme.typography.bodyMedium,
              )
            }
          } else {
            items(
              items = candidates,
              key = { (book, _) -> "batch-${book.source.name}:${book.sourceId}" },
            ) { (book, draft) ->
              val key = book.organizationKey()
              Card(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 12.dp)
                  .clickable(enabled = !batch.applying) { editingCandidate = book },
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.Top,
                ) {
                  Checkbox(
                    checked = key in batch.selectedKeys,
                    onCheckedChange = { onToggleSelection(key) },
                    enabled = !batch.applying,
                  )
                  Column(
                    modifier = Modifier
                      .weight(1f)
                      .padding(start = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                  ) {
                    Text(
                      book.title,
                      style = MaterialTheme.typography.titleSmall,
                      maxLines = 2,
                      overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                      "コレクション: ${draft.collectionNames.joinToString().ifEmpty { "候補なし" }}",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 2,
                      overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                      "タグ: ${draft.tagNames.joinToString().ifEmpty { "候補なし" }}",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 2,
                      overflow = TextOverflow.Ellipsis,
                    )
                    suggestions[key]?.reason?.let { reason ->
                      Text(
                        reason,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                      )
                    }
                    Text(
                      "タップして候補を編集",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.primary,
                    )
                  }
                }
              }
            }
          }

          if (batch.failures.isNotEmpty()) {
            item {
              Text(
                "生成に失敗した蔵書",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleSmall,
              )
            }
            items(
              items = batch.failures.entries.toList(),
              key = { (key, _) -> "batch-failure-${key.source.name}:${key.sourceId}" },
            ) { (key, error) ->
              Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
              ) {
                Text(
                  booksByKey[key]?.title ?: "蔵書を特定できません",
                  style = MaterialTheme.typography.bodyMedium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  error,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
          item { Spacer(Modifier.height(12.dp)) }
        }

        HorizontalDivider()
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Button(
            onClick = onApply,
            enabled = batch.selectedKeys.isNotEmpty() && !batch.running && !batch.applying,
          ) {
            if (batch.applying) {
              CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
            } else {
              Text("選択 ${batch.selectedKeys.size} 件を適用")
            }
          }
        }
      }
    }
  }

  editingCandidate?.let { book ->
    val key = book.organizationKey()
    batch.drafts[key]?.let { draft ->
      LibraryOrganizationBatchCandidateEditorDialog(
        book = book,
        draft = draft,
        onSave = { updated ->
          onUpdateDraft(key, updated)
          editingCandidate = null
        },
        onDismiss = { editingCandidate = null },
      )
    }
  }
}

@Composable
private fun LibraryOrganizationBatchCandidateEditorDialog(
  book: LibraryBook,
  draft: LibraryOrganizationBatchDraft,
  onSave: (LibraryOrganizationBatchDraft) -> Unit,
  onDismiss: () -> Unit,
) {
  var tagText by remember(book.organizationKey(), draft.tagNames) {
    mutableStateOf(draft.tagNames.joinToString(", "))
  }
  var collectionText by remember(book.organizationKey(), draft.collectionNames) {
    mutableStateOf(draft.collectionNames.joinToString(", "))
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("AI候補を編集") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
          book.title,
          style = MaterialTheme.typography.titleSmall,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
        )
        OutlinedTextField(
          value = collectionText,
          onValueChange = { collectionText = it },
          modifier = Modifier.fillMaxWidth(),
          label = { Text("コレクション") },
          supportingText = { Text("一括適用する前の候補だけを編集します") },
        )
        OutlinedTextField(
          value = tagText,
          onValueChange = { tagText = it },
          modifier = Modifier.fillMaxWidth(),
          label = { Text("タグ") },
        )
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          onSave(
            LibraryOrganizationBatchDraft(
              tagNames = splitOrganizationNames(tagText),
              collectionNames = splitOrganizationNames(collectionText),
            ),
          )
        },
      ) { Text("候補を更新") }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("キャンセル") }
    },
  )
}

@Composable
private fun LibraryOrganizationBookRow(
  book: LibraryBook,
  organization: LibraryItemOrganization,
  hasAiSuggestion: Boolean,
  onClick: () -> Unit,
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 12.dp)
      .clickable(onClick = onClick),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
      ) {
        Text(
          book.title,
          modifier = Modifier.weight(1f),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Medium,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        if (hasAiSuggestion) {
          Text(
            "AI候補あり",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }
      Text(
        book.source.label + book.authors.takeIf(List<String>::isNotEmpty)
          ?.joinToString(prefix = " / ", separator = ", ")
          .orEmpty(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      val collections = organization.collections.joinToString { it.name }
      val tags = organization.tags.joinToString { it.name }
      Text(
        if (collections.isEmpty()) "コレクション: 未設定" else "コレクション: $collections",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        if (tags.isEmpty()) "タグ: 未設定" else "タグ: $tags",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        "読書状態: ${organization.readingStatus?.label ?: "未設定"}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun LibraryOrganizationEditorDialog(
  book: LibraryBook,
  organization: LibraryItemOrganization,
  existingTags: List<String>,
  existingCollections: List<String>,
  suggestion: LibraryOrganizationSuggestion?,
  saving: Boolean,
  suggesting: Boolean,
  aiEnabled: Boolean,
  onSuggest: () -> Unit,
  onSave: (LibraryOrganizationDraft) -> Unit,
  onDismiss: () -> Unit,
) {
  var tagText by remember(book.organizationKey(), organization.tags) {
    mutableStateOf(organization.tags.joinToString(", ") { it.name })
  }
  var collectionText by remember(book.organizationKey(), organization.collections) {
    mutableStateOf(organization.collections.joinToString(", ") { it.name })
  }
  var readingStatus by remember(book.organizationKey(), organization.readingStatus) {
    mutableStateOf(organization.readingStatus)
  }

  LaunchedEffect(suggestion) {
    val candidate = suggestion ?: return@LaunchedEffect
    tagText = mergeOrganizationNames(tagText, candidate.tagNames)
    collectionText = mergeOrganizationNames(collectionText, candidate.collectionNames)
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("蔵書を整理") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          book.title,
          style = MaterialTheme.typography.titleSmall,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
        )
        OutlinedTextField(
          value = collectionText,
          onValueChange = { collectionText = it },
          modifier = Modifier.fillMaxWidth(),
          label = { Text("コレクション") },
          supportingText = {
            val examples = existingCollections.take(8).joinToString(", ")
            Text(if (examples.isEmpty()) "カンマ区切りで複数指定できます" else "既存: $examples")
          },
        )
        OutlinedTextField(
          value = tagText,
          onValueChange = { tagText = it },
          modifier = Modifier.fillMaxWidth(),
          label = { Text("タグ") },
          supportingText = {
            val examples = existingTags.take(8).joinToString(", ")
            Text(if (examples.isEmpty()) "カンマ区切りで複数指定できます" else "既存: $examples")
          },
        )
        Text("読書状態", style = MaterialTheme.typography.labelLarge)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          FilterChip(
            selected = readingStatus == null,
            onClick = { readingStatus = null },
            label = { Text("未設定") },
          )
          LibraryReadingStatus.entries.forEach { status ->
            FilterChip(
              selected = readingStatus == status,
              onClick = { readingStatus = status },
              label = { Text(status.label) },
            )
          }
        }
        Button(
          onClick = onSuggest,
          enabled = aiEnabled && !suggesting && !saving,
        ) {
          if (suggesting) {
            CircularProgressIndicator(
              modifier = Modifier.height(18.dp),
              strokeWidth = 2.dp,
            )
          } else {
            Text(if (aiEnabled) "AIで整理候補を作る" else "一括AI解析中")
          }
        }
        suggestion?.let { candidate ->
          Text(
            "AI候補を入力欄へ反映しました。保存前に内容を確認してください。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
          )
          candidate.reason?.let { reason ->
            Text(
              reason,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        enabled = !saving && !suggesting,
        onClick = {
          onSave(
            LibraryOrganizationDraft(
              tagNames = splitOrganizationNames(tagText),
              collectionNames = splitOrganizationNames(collectionText),
              readingStatus = readingStatus,
            ),
          )
        },
      ) {
        Text(if (saving) "保存中" else "保存")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss, enabled = !saving) { Text("キャンセル") }
    },
  )
}

internal fun splitOrganizationNames(value: String): List<String> = value
  .split(',', '、', '\n')
  .map(String::trim)
  .filter(String::isNotEmpty)
  .distinctBy { it.lowercase() }

private fun mergeOrganizationNames(
  current: String,
  suggested: List<String>,
): String = (splitOrganizationNames(current) + suggested)
  .map(String::trim)
  .filter(String::isNotEmpty)
  .distinctBy { it.lowercase() }
  .joinToString(", ")
