package dev.terashima.yomitorirss.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
  onStartBatch: (List<LibraryBook>) -> Unit,
  onPauseBatch: () -> Unit,
  onResumeBatch: () -> Unit,
  onAcceptCandidate: (LibraryBook, LibraryOrganizationCandidate, List<String>, List<String>) -> Unit,
  onDeferCandidate: (LibraryOrganizationCandidate) -> Unit,
  onRejectCandidate: (LibraryOrganizationCandidate) -> Unit,
  onReopenCandidate: (LibraryOrganizationCandidate) -> Unit,
  onRetryCandidate: (LibraryOrganizationCandidate) -> Unit,
  onUpdateCandidate: (LibraryOrganizationCandidate, List<String>, List<String>) -> Unit,
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
            "AIの一括解析はバックグラウンドで継続します。生成済み候補は保存され、解析中でも順次仕分けできます。",
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          LibraryBackgroundOrganizationCard(
            ready = state.initialized && !state.loading,
            unorganizedCount = unorganizedCount,
            batch = state.batch,
            onStart = {
              onStartBatch(books)
              batchReviewVisible = true
            },
            onReview = { batchReviewVisible = true },
            onPause = onPauseBatch,
            onResume = onResumeBatch,
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
      aiEnabled = state.batch?.status != LibraryOrganizationBatchStatus.RUNNING,
      onSuggest = { onSuggest(book) },
      onSave = { draft ->
        onSave(book, draft)
        editingBook = null
      },
      onDismiss = { editingBook = null },
    )
  }

  if (batchReviewVisible) {
    LibraryOrganizationCandidateReviewDialog(
      books = books,
      batch = state.batch,
      actionBook = state.candidateActionBook,
      onAccept = onAcceptCandidate,
      onDefer = onDeferCandidate,
      onReject = onRejectCandidate,
      onReopen = onReopenCandidate,
      onRetry = onRetryCandidate,
      onUpdate = onUpdateCandidate,
      onDismiss = { batchReviewVisible = false },
    )
  }
}

@Composable
private fun LibraryBackgroundOrganizationCard(
  ready: Boolean,
  unorganizedCount: Int,
  batch: LibraryOrganizationBatchSnapshot?,
  onStart: () -> Unit,
  onReview: () -> Unit,
  onPause: () -> Unit,
  onResume: () -> Unit,
) {
  val canStartNew = batch == null || (
    batch.status == LibraryOrganizationBatchStatus.COMPLETED &&
      batch.candidates.none { it.status in ACTIVE_OR_REVIEW_STATUSES }
    )
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
      if (batch == null) {
        Text(
          "未整理の蔵書を端末上のローカルAIで順番に解析します。画面を閉じても処理と候補は保持されます。",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        Text(
          "${batch.status.label} · ${batch.processed} / ${batch.total} 冊解析済み",
          style = MaterialTheme.typography.bodyMedium,
        )
        Text(
          "未確認 ${batch.pendingReview} · 保留 ${batch.deferred}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        when (batch?.status) {
          LibraryOrganizationBatchStatus.RUNNING -> {
            Button(onClick = onReview) { Text("候補を仕分け") }
            TextButton(onClick = onPause) { Text("解析を一時停止") }
          }
          LibraryOrganizationBatchStatus.PAUSED -> {
            Button(onClick = onReview) { Text("候補を仕分け") }
            TextButton(onClick = onResume) { Text("解析を再開") }
          }
          LibraryOrganizationBatchStatus.COMPLETED -> {
            Button(onClick = onReview) { Text("候補を仕分け") }
            if (canStartNew && unorganizedCount > 0) {
              TextButton(onClick = onStart) { Text("未整理を再解析") }
            }
          }
          null -> {
            Button(
              onClick = onStart,
              enabled = ready && unorganizedCount > 0,
            ) {
              Text(
                when {
                  !ready -> "整理情報を読み込み中"
                  unorganizedCount == 0 -> "未整理の蔵書はありません"
                  else -> "未整理 $unorganizedCount 冊をバックグラウンド解析"
                },
              )
            }
          }
        }
      }
    }
  }
}

private enum class CandidateReviewFilter(
  val label: String,
  val statuses: Set<LibraryOrganizationCandidateStatus>?,
) {
  REVIEW("未確認", setOf(LibraryOrganizationCandidateStatus.PENDING_REVIEW)),
  DEFERRED("保留", setOf(LibraryOrganizationCandidateStatus.DEFERRED)),
  PROCESSING(
    "解析中",
    setOf(
      LibraryOrganizationCandidateStatus.QUEUED,
      LibraryOrganizationCandidateStatus.PROCESSING,
    ),
  ),
  APPLIED("採用済み", setOf(LibraryOrganizationCandidateStatus.APPLIED)),
  REJECTED("却下", setOf(LibraryOrganizationCandidateStatus.REJECTED)),
  PROBLEM(
    "要確認",
    setOf(
      LibraryOrganizationCandidateStatus.FAILED,
      LibraryOrganizationCandidateStatus.SKIPPED,
    ),
  ),
  ALL("すべて", null),
}

@Composable
private fun LibraryOrganizationCandidateReviewDialog(
  books: List<LibraryBook>,
  batch: LibraryOrganizationBatchSnapshot?,
  actionBook: LibraryBookKey?,
  onAccept: (LibraryBook, LibraryOrganizationCandidate, List<String>, List<String>) -> Unit,
  onDefer: (LibraryOrganizationCandidate) -> Unit,
  onReject: (LibraryOrganizationCandidate) -> Unit,
  onReopen: (LibraryOrganizationCandidate) -> Unit,
  onRetry: (LibraryOrganizationCandidate) -> Unit,
  onUpdate: (LibraryOrganizationCandidate, List<String>, List<String>) -> Unit,
  onDismiss: () -> Unit,
) {
  var filterName by rememberSaveable { mutableStateOf(CandidateReviewFilter.REVIEW.name) }
  var editingCandidate by remember { mutableStateOf<LibraryOrganizationCandidate?>(null) }
  val filter = CandidateReviewFilter.valueOf(filterName)
  val booksByKey = remember(books) { books.associateBy(LibraryBook::organizationKey) }
  val candidates = remember(batch, filter) {
    batch?.candidates.orEmpty()
      .filter { candidate -> filter.statuses?.contains(candidate.status) ?: true }
      .sortedWith(
        compareBy<LibraryOrganizationCandidate> { candidateStatusOrder(it.status) }
          .thenByDescending(LibraryOrganizationCandidate::updatedAt),
      )
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(
      usePlatformDefaultWidth = false,
      decorFitsSystemWindows = false,
    ),
  ) {
    Surface(Modifier.fillMaxSize()) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .windowInsetsPadding(WindowInsets.safeDrawing),
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
              "AI整理候補を仕分け",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              batch?.let {
                "${it.status.label} · ${it.processed}/${it.total} · 未確認 ${it.pendingReview} · 保留 ${it.deferred}"
              } ?: "一括整理はまだありません",
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
          CandidateReviewFilter.entries.forEach { candidateFilter ->
            val count = batch?.candidates.orEmpty().count { candidate ->
              candidateFilter.statuses?.contains(candidate.status) ?: true
            }
            FilterChip(
              selected = filter == candidateFilter,
              onClick = { filterName = candidateFilter.name },
              label = { Text("${candidateFilter.label} $count") },
            )
          }
        }
        HorizontalDivider()

        if (candidates.isEmpty()) {
          Text(
            when {
              batch == null -> "一括AI解析を開始すると候補がここに保存されます。"
              batch.status == LibraryOrganizationBatchStatus.RUNNING -> "候補を生成中です。生成された本から順次ここに表示されます。"
              else -> "この条件に一致する候補はありません。"
            },
            modifier = Modifier.padding(20.dp),
            style = MaterialTheme.typography.bodyMedium,
          )
        } else {
          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            item { Spacer(Modifier.height(4.dp)) }
            items(
              items = candidates,
              key = { "candidate-${it.batchId}:${it.key.source}:${it.key.sourceId}" },
            ) { candidate ->
              val book = booksByKey[candidate.key]
              LibraryOrganizationCandidateRow(
                candidate = candidate,
                book = book,
                busy = actionBook == candidate.key,
                onAccept = {
                  if (book != null) {
                    onAccept(book, candidate, candidate.tagNames, candidate.collectionNames)
                  }
                },
                onEdit = { editingCandidate = candidate },
                onDefer = { onDefer(candidate) },
                onReject = { onReject(candidate) },
                onReopen = { onReopen(candidate) },
                onRetry = { onRetry(candidate) },
              )
            }
            item { Spacer(Modifier.height(24.dp)) }
          }
        }
      }
    }
  }

  editingCandidate?.let { candidate ->
    val book = booksByKey[candidate.key]
    LibraryOrganizationCandidateEditorDialog(
      title = book?.title ?: "蔵書の整理候補",
      candidate = candidate,
      busy = actionBook == candidate.key,
      canAccept = book != null,
      onSaveDraft = { tags, collections ->
        onUpdate(candidate, tags, collections)
        editingCandidate = null
      },
      onAccept = { tags, collections ->
        if (book != null) onAccept(book, candidate, tags, collections)
        editingCandidate = null
      },
      onDismiss = { editingCandidate = null },
    )
  }
}

@Composable
private fun LibraryOrganizationCandidateRow(
  candidate: LibraryOrganizationCandidate,
  book: LibraryBook?,
  busy: Boolean,
  onAccept: () -> Unit,
  onEdit: () -> Unit,
  onDefer: () -> Unit,
  onReject: () -> Unit,
  onReopen: () -> Unit,
  onRetry: () -> Unit,
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 12.dp),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
      ) {
        Text(
          book?.title ?: "${candidate.key.source.label} の蔵書",
          modifier = Modifier.weight(1f),
          style = MaterialTheme.typography.titleSmall,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          candidate.status.label,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary,
        )
      }
      if (candidate.tagNames.isNotEmpty()) {
        Text(
          "タグ: ${candidate.tagNames.joinToString()}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (candidate.collectionNames.isNotEmpty()) {
        Text(
          "コレクション: ${candidate.collectionNames.joinToString()}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      candidate.reason?.let { reason ->
        Text(
          reason,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      candidate.error?.let { error ->
        Text(
          error,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
      }

      if (busy) {
        CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
      } else {
        Row(
          modifier = Modifier.horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          when (candidate.status) {
            LibraryOrganizationCandidateStatus.PENDING_REVIEW -> {
              Button(onClick = onAccept, enabled = book != null) { Text("採用") }
              TextButton(onClick = onEdit) { Text("編集") }
              TextButton(onClick = onDefer) { Text("保留") }
              TextButton(onClick = onReject) { Text("却下") }
            }
            LibraryOrganizationCandidateStatus.DEFERRED -> {
              Button(onClick = onAccept, enabled = book != null) { Text("採用") }
              TextButton(onClick = onEdit) { Text("編集") }
              TextButton(onClick = onReopen) { Text("未確認へ") }
              TextButton(onClick = onReject) { Text("却下") }
            }
            LibraryOrganizationCandidateStatus.REJECTED -> {
              TextButton(onClick = onReopen) { Text("未確認へ戻す") }
            }
            LibraryOrganizationCandidateStatus.FAILED,
            LibraryOrganizationCandidateStatus.SKIPPED -> {
              TextButton(onClick = onRetry) { Text("再解析") }
              TextButton(onClick = onReject) { Text("却下して完了") }
            }
            LibraryOrganizationCandidateStatus.QUEUED,
            LibraryOrganizationCandidateStatus.PROCESSING,
            LibraryOrganizationCandidateStatus.APPLIED -> Unit
          }
        }
      }
    }
  }
}

@Composable
private fun LibraryOrganizationCandidateEditorDialog(
  title: String,
  candidate: LibraryOrganizationCandidate,
  busy: Boolean,
  canAccept: Boolean,
  onSaveDraft: (List<String>, List<String>) -> Unit,
  onAccept: (List<String>, List<String>) -> Unit,
  onDismiss: () -> Unit,
) {
  var tagText by remember(candidate.key, candidate.updatedAt) {
    mutableStateOf(candidate.tagNames.joinToString(", "))
  }
  var collectionText by remember(candidate.key, candidate.updatedAt) {
    mutableStateOf(candidate.collectionNames.joinToString(", "))
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("AI整理候補を編集") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
          value = collectionText,
          onValueChange = { collectionText = it },
          modifier = Modifier.fillMaxWidth(),
          label = { Text("コレクション") },
        )
        OutlinedTextField(
          value = tagText,
          onValueChange = { tagText = it },
          modifier = Modifier.fillMaxWidth(),
          label = { Text("タグ") },
        )
        Text(
          "候補だけ保存すれば後で再開できます。採用すると実際の蔵書整理情報へ反映されます。",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
    confirmButton = {
      TextButton(
        enabled = !busy && canAccept,
        onClick = {
          onAccept(
            splitOrganizationNames(tagText),
            splitOrganizationNames(collectionText),
          )
        },
      ) { Text("修正して採用") }
    },
    dismissButton = {
      Row {
        TextButton(
          enabled = !busy,
          onClick = {
            onSaveDraft(
              splitOrganizationNames(tagText),
              splitOrganizationNames(collectionText),
            )
          },
        ) { Text("候補だけ保存") }
        TextButton(onClick = onDismiss, enabled = !busy) { Text("キャンセル") }
      }
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

private fun candidateStatusOrder(status: LibraryOrganizationCandidateStatus): Int = when (status) {
  LibraryOrganizationCandidateStatus.PENDING_REVIEW -> 0
  LibraryOrganizationCandidateStatus.DEFERRED -> 1
  LibraryOrganizationCandidateStatus.PROCESSING -> 2
  LibraryOrganizationCandidateStatus.QUEUED -> 3
  LibraryOrganizationCandidateStatus.FAILED -> 4
  LibraryOrganizationCandidateStatus.SKIPPED -> 5
  LibraryOrganizationCandidateStatus.APPLIED -> 6
  LibraryOrganizationCandidateStatus.REJECTED -> 7
}

private val ACTIVE_OR_REVIEW_STATUSES = setOf(
  LibraryOrganizationCandidateStatus.QUEUED,
  LibraryOrganizationCandidateStatus.PROCESSING,
  LibraryOrganizationCandidateStatus.PENDING_REVIEW,
  LibraryOrganizationCandidateStatus.DEFERRED,
  LibraryOrganizationCandidateStatus.FAILED,
  LibraryOrganizationCandidateStatus.SKIPPED,
)