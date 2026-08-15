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
import androidx.compose.foundation.layout.weight
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
  onDismissMessage: () -> Unit,
  onDismiss: () -> Unit,
) {
  var selectedFilterName by rememberSaveable { mutableStateOf(LibraryOrganizationFilter.UNORGANIZED.name) }
  var editingBook by remember { mutableStateOf<LibraryBook?>(null) }
  val selectedFilter = LibraryOrganizationFilter.valueOf(selectedFilterName)
  val filteredBooks = remember(books, state.snapshot, selectedFilter) {
    filterLibraryBooksForOrganization(books, state.snapshot, selectedFilter)
      .sortedWith(compareBy<LibraryBook> { it.title.lowercase() }.thenBy { it.sourceId })
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
              val unorganized = filterLibraryBooksForOrganization(
                books,
                state.snapshot,
                LibraryOrganizationFilter.UNORGANIZED,
              ).size
              Text(
                "未整理 $unorganized 冊 / 全 ${books.size} 冊",
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
      onSuggest = { onSuggest(book) },
      onSave = { draft ->
        onSave(book, draft)
        editingBook = null
      },
      onDismiss = { editingBook = null },
    )
  }
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
          enabled = !suggesting && !saving,
        ) {
          if (suggesting) {
            CircularProgressIndicator(
              modifier = Modifier.height(18.dp),
              strokeWidth = 2.dp,
            )
          } else {
            Text("AIで整理候補を作る")
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
