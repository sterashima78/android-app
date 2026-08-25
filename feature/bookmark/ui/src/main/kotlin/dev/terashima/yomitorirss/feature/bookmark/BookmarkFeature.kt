package dev.terashima.yomitorirss.feature.bookmark

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.article.ArticleList
import dev.terashima.yomitorirss.feature.article.ArticleMenuAction
import dev.terashima.yomitorirss.feature.article.ContentType
import dev.terashima.yomitorirss.feature.article.SwipeChoice

enum class BookmarkTab(val label: String) {
  BOOKMARKS("一覧"),
  FOLDERS("フォルダ"),
  TAGS("タグ"),
  IMPORT("インポート"),
}

@Composable
fun BookmarkScreen(
  modifier: Modifier,
  tab: BookmarkTab,
  state: BookmarkUiState,
  onTagSelected: (String?) -> Unit,
  onFolderSelected: (String?) -> Unit,
  onOpen: (Article) -> Unit,
  onSummarize: (Article) -> Unit,
  onEditTags: (Article) -> Unit,
  onMoveFolder: (Article) -> Unit,
  onMoveToLibrary: (Article) -> Unit,
  onSetContentType: (Article, ContentType?) -> Unit,
  onUnsave: (Article) -> Unit,
  onCreateFolder: (String) -> Unit,
  onRenameFolder: (BookmarkFolder, String) -> Unit,
  onDeleteFolder: (BookmarkFolder) -> Unit,
  onCreateTag: (String) -> Unit,
  onRenameTag: (Tag, String) -> Unit,
  onDeleteTag: (Tag) -> Unit,
  onDeleteUnusedTags: () -> Unit,
  onReprocessEnrichment: () -> Unit,
  isReprocessingEnrichment: Boolean,
  onImportCsv: () -> Unit,
  onImportHtml: () -> Unit,
) {
  when (tab) {
    BookmarkTab.BOOKMARKS -> BookmarkSavedScreen(
      modifier = modifier,
      state = state,
      onTagSelected = onTagSelected,
      onFolderSelected = onFolderSelected,
      onOpen = onOpen,
      onSummarize = onSummarize,
      onEditTags = onEditTags,
      onMoveFolder = onMoveFolder,
      onMoveToLibrary = onMoveToLibrary,
      onSetContentType = onSetContentType,
      onUnsave = onUnsave,
      onReprocessEnrichment = onReprocessEnrichment,
      isReprocessingEnrichment = isReprocessingEnrichment,
    )

    BookmarkTab.FOLDERS -> FolderManagerScreen(
      modifier = modifier,
      folders = state.folders,
      onCreate = onCreateFolder,
      onRename = onRenameFolder,
      onDelete = onDeleteFolder,
    )

    BookmarkTab.TAGS -> TagManagerScreen(
      modifier = modifier,
      tags = state.tags,
      bookmarks = state.bookmarkDetails.values.toList(),
      hiddenArticleIds = state.hiddenArticleIds,
      onOpen = onOpen,
      onSummarize = onSummarize,
      onEditTags = onEditTags,
      onMoveFolder = onMoveFolder,
      onSetContentType = onSetContentType,
      onUnsave = onUnsave,
      onCreate = onCreateTag,
      onRename = onRenameTag,
      onDelete = onDeleteTag,
      onDeleteUnused = onDeleteUnusedTags,
    )

    BookmarkTab.IMPORT -> BookmarkImportScreen(
      modifier = modifier,
      tagCount = state.tags.size,
      onImportCsv = onImportCsv,
      onImportHtml = onImportHtml,
    )
  }
}

@Composable
private fun BookmarkSavedScreen(
  modifier: Modifier,
  state: BookmarkUiState,
  onTagSelected: (String?) -> Unit,
  onFolderSelected: (String?) -> Unit,
  onOpen: (Article) -> Unit,
  onSummarize: (Article) -> Unit,
  onEditTags: (Article) -> Unit,
  onMoveFolder: (Article) -> Unit,
  onMoveToLibrary: (Article) -> Unit,
  onSetContentType: (Article, ContentType?) -> Unit,
  onUnsave: (Article) -> Unit,
  onReprocessEnrichment: () -> Unit,
  isReprocessingEnrichment: Boolean,
) {
  var confirmingReprocess by remember { mutableStateOf(false) }

  Column(modifier.fillMaxSize()) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
      horizontalArrangement = Arrangement.End,
    ) {
      TextButton(
        onClick = { confirmingReprocess = true },
        enabled = state.bookmarkDetails.isNotEmpty() && !isReprocessingEnrichment,
      ) {
        Icon(Icons.Default.Refresh, contentDescription = null)
        Text(if (isReprocessingEnrichment) "再実行を予約中…" else "要約・タグを一括再実行")
      }
    }

    LazyRow(
      modifier = Modifier.fillMaxWidth().height(54.dp),
      contentPadding = PaddingValues(horizontal = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      item {
        FilterChip(
          selected = state.selectedFolderId == null,
          onClick = { onFolderSelected(null) },
          label = { Text("すべて") },
        )
      }
      item {
        FilterChip(
          selected = state.selectedFolderId == UNCATEGORIZED_FOLDER_ID,
          onClick = { onFolderSelected(UNCATEGORIZED_FOLDER_ID) },
          label = { Text("未分類") },
        )
      }
      items(state.folders, key = BookmarkFolder::id) { folder ->
        FilterChip(
          selected = state.selectedFolderId == folder.id,
          onClick = { onFolderSelected(folder.id) },
          label = { Text(folder.name) },
        )
      }
    }

    if (state.tags.isNotEmpty()) {
      LazyRow(
        modifier = Modifier.fillMaxWidth().height(54.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        item {
          FilterChip(
            selected = state.selectedTagId == null,
            onClick = { onTagSelected(null) },
            label = { Text("全タグ") },
          )
        }
        items(state.tags, key = Tag::id) { tag ->
          FilterChip(
            selected = state.selectedTagId == tag.id,
            onClick = { onTagSelected(tag.id) },
            label = { Text(tag.name) },
          )
        }
      }
    }

    val visible = state.saved.filterNot { it.article.id in state.hiddenArticleIds }
    ArticleList(
      modifier = Modifier.weight(1f),
      articles = visible.map(BookmarkedArticle::article),
      bookmarkDetails = visible.associateBy { it.article.id },
      emptyText = "ブックマークはありません",
      left = SwipeChoice("ブックマーク解除", MaterialTheme.colorScheme.error, onUnsave),
      onOpen = onOpen,
      onSummarize = onSummarize,
      onEditTags = onEditTags,
      onMoveFolder = onMoveFolder,
      onSetContentType = onSetContentType,
      extraMenuActions = { article ->
        listOf(
          ArticleMenuAction("蔵書へ移動") { onMoveToLibrary(article) },
        )
      },
    )
  }

  if (confirmingReprocess) {
    AlertDialog(
      onDismissRequest = { confirmingReprocess = false },
      title = { Text("要約とタグ付けを一括再実行") },
      text = {
        Text(
          "自動AI処理の対象となるブックマークの要約を再生成し、その要約を使ってタグ付けも再実行します。" +
            "再生成に成功した記事の既存タグは生成されたタグで置き換えます。処理中の記事は重複してキューへ追加しません。",
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            confirmingReprocess = false
            onReprocessEnrichment()
          },
        ) {
          Text("再実行")
        }
      },
      dismissButton = {
        TextButton(onClick = { confirmingReprocess = false }) {
          Text("キャンセル")
        }
      },
    )
  }
}
