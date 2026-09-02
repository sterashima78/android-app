package dev.terashima.yomitorirss.feature.bookmark

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.article.ArticleList
import dev.terashima.yomitorirss.feature.article.ContentType
import dev.terashima.yomitorirss.feature.article.SwipeChoice

@Composable
internal fun TagManagerScreen(
  tags: List<Tag>,
  bookmarks: List<BookmarkedArticle>,
  hiddenArticleIds: Set<String>,
  onOpen: (Article) -> Unit,
  onSummarize: (Article) -> Unit,
  onEditTags: (Article) -> Unit,
  onMoveFolder: (Article) -> Unit,
  onSetContentType: (Article, ContentType?) -> Unit,
  onUnsave: (Article) -> Unit,
  onCreate: (String) -> Unit,
  onRename: (Tag, String) -> Unit,
  onDelete: (Tag) -> Unit,
  onDeleteUnused: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var newName by remember { mutableStateOf("") }
  var selectedTagId by remember { mutableStateOf<String?>(null) }
  var editing by remember { mutableStateOf<Tag?>(null) }
  var editingName by remember { mutableStateOf("") }
  var confirmingDeleteUnused by remember { mutableStateOf(false) }

  val articleCounts = remember(bookmarks, hiddenArticleIds) {
    countArticlesByTag(bookmarks, hiddenArticleIds)
  }
  val selectedTag = tags.firstOrNull { it.id == selectedTagId }
  val selectedBookmarks = remember(bookmarks, selectedTagId, hiddenArticleIds) {
    selectedTagId?.let { articlesWithTag(bookmarks, it, hiddenArticleIds) }.orEmpty()
  }

  Column(
    modifier = modifier.fillMaxSize(),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      OutlinedTextField(
        value = newName,
        onValueChange = { newName = it },
        label = { Text("新しいタグ") },
        singleLine = true,
        modifier = Modifier.weight(1f),
      )
      IconButton(
        onClick = {
          onCreate(newName)
          newName = ""
        },
        enabled = newName.isNotBlank(),
      ) {
        Icon(Icons.Default.Add, "追加")
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
      horizontalArrangement = Arrangement.End,
    ) {
      TextButton(
        onClick = { confirmingDeleteUnused = true },
        enabled = tags.isNotEmpty(),
      ) {
        Icon(Icons.Default.Delete, contentDescription = null)
        Text("未使用タグを一括削除")
      }
    }

    if (tags.isEmpty()) {
      Box(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          "タグはありません",
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    } else {
      LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 88.dp),
        modifier = Modifier.weight(1f).fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        items(tags, key = Tag::id) { tag ->
          CompactTagTile(
            tag = tag,
            articleCount = articleCounts[tag.id] ?: 0,
            onClick = { selectedTagId = tag.id },
          )
        }
      }
    }
  }

  selectedTag?.let { tag ->
    Dialog(
      onDismissRequest = { selectedTagId = null },
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
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              "${tag.name}  ${selectedBookmarks.size}件",
              style = MaterialTheme.typography.titleMedium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f),
            )
            IconButton(
              onClick = {
                editing = tag
                editingName = tag.name
              },
            ) {
              Icon(Icons.Default.Edit, "タグ名を変更")
            }
            IconButton(
              onClick = {
                selectedTagId = null
                onDelete(tag)
              },
            ) {
              Icon(Icons.Default.Delete, "タグを削除")
            }
            IconButton(onClick = { selectedTagId = null }) {
              Icon(Icons.Default.Close, "閉じる")
            }
          }

          ArticleList(
            modifier = Modifier.weight(1f),
            articles = selectedBookmarks.map(BookmarkedArticle::article),
            bookmarkDetails = selectedBookmarks.associateBy { it.article.id },
            emptyText = "このタグの記事はありません",
            left = SwipeChoice("ブックマーク解除", MaterialTheme.colorScheme.error, onUnsave),
            onOpen = onOpen,
            onSummarize = onSummarize,
            onEditTags = onEditTags,
            onMoveFolder = onMoveFolder,
            onSetContentType = onSetContentType,
          )
        }
      }
    }
  }

  if (confirmingDeleteUnused) {
    AlertDialog(
      onDismissRequest = { confirmingDeleteUnused = false },
      title = { Text("未使用タグを削除") },
      text = {
        Text("記事に1件も付いていないタグをすべて削除します。記事があるタグは削除されません。この操作は元に戻せません。")
      },
      confirmButton = {
        TextButton(
          onClick = {
            confirmingDeleteUnused = false
            onDeleteUnused()
          },
        ) {
          Text("削除")
        }
      },
      dismissButton = {
        TextButton(onClick = { confirmingDeleteUnused = false }) {
          Text("キャンセル")
        }
      },
    )
  }

  editing?.let { tag ->
    AlertDialog(
      onDismissRequest = { editing = null },
      title = { Text("タグ名を変更") },
      text = {
        OutlinedTextField(
          value = editingName,
          onValueChange = { editingName = it },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            onRename(tag, editingName)
            editing = null
          },
          enabled = editingName.isNotBlank(),
        ) {
          Text("変更")
        }
      },
      dismissButton = {
        TextButton(onClick = { editing = null }) {
          Text("キャンセル")
        }
      },
    )
  }
}

@Composable
private fun CompactTagTile(
  tag: Tag,
  articleCount: Int,
  onClick: () -> Unit,
) {
  Surface(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.small,
    color = MaterialTheme.colorScheme.surfaceVariant,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 40.dp)
        .padding(horizontal = 8.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = tag.name,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      Text(
        text = articleCount.toString(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

internal fun countArticlesByTag(
  bookmarks: List<BookmarkedArticle>,
  hiddenArticleIds: Set<String> = emptySet(),
): Map<String, Int> = bookmarks
  .asSequence()
  .filterNot { it.article.id in hiddenArticleIds }
  .flatMap { bookmark -> bookmark.tags.asSequence().map(Tag::id) }
  .groupingBy { it }
  .eachCount()

internal fun articlesWithTag(
  bookmarks: List<BookmarkedArticle>,
  tagId: String,
  hiddenArticleIds: Set<String> = emptySet(),
): List<BookmarkedArticle> = bookmarks.filter { bookmark ->
  bookmark.article.id !in hiddenArticleIds && bookmark.tags.any { it.id == tagId }
}
