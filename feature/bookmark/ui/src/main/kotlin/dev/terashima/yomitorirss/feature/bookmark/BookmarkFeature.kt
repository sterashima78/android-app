package dev.terashima.yomitorirss.feature.bookmark

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.article.ArticleList
import dev.terashima.yomitorirss.feature.article.SwipeChoice
import dev.terashima.yomitorirss.feature.bookmark.FolderManagerScreen
import dev.terashima.yomitorirss.feature.bookmark.TagManagerScreen

enum class BookmarkTab(val label: String) {
  BOOKMARKS("一覧"),
  FOLDERS("フォルダ"),
  TAGS("タグ"),
  HISTORY("履歴"),
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
  onUnsave: (Article) -> Unit,
  onMarkUnread: (Article) -> Unit,
  onCreateFolder: (String) -> Unit,
  onRenameFolder: (BookmarkFolder, String) -> Unit,
  onDeleteFolder: (BookmarkFolder) -> Unit,
  onCreateTag: (String) -> Unit,
  onRenameTag: (Tag, String) -> Unit,
  onDeleteTag: (Tag) -> Unit,
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
      onUnsave = onUnsave,
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
      selectedTagId = state.selectedTagId,
      hiddenArticleIds = state.hiddenArticleIds,
      onTagSelected = onTagSelected,
      onOpen = onOpen,
      onSummarize = onSummarize,
      onEditTags = onEditTags,
      onMoveFolder = onMoveFolder,
      onUnsave = onUnsave,
      onCreate = onCreateTag,
      onRename = onRenameTag,
      onDelete = onDeleteTag,
    )

    BookmarkTab.HISTORY -> ArticleList(
      modifier = modifier,
      articles = state.history.filterNot { it.id in state.hiddenArticleIds },
      bookmarkDetails = state.bookmarkDetails,
      emptyText = "履歴はありません",
      right = SwipeChoice("未読に戻す", MaterialTheme.colorScheme.secondary, onMarkUnread),
      onOpen = onOpen,
      onSummarize = onSummarize,
      onEditTags = onEditTags,
      onMoveFolder = onMoveFolder,
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
  onUnsave: (Article) -> Unit,
) {
  Column(modifier.fillMaxSize()) {
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
    )
  }
}
