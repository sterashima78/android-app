package dev.terashima.yomitorirss.feature.bookmark

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
  modifier: Modifier = Modifier,
) {
  var newName by remember { mutableStateOf("") }
  var selectedTagId by remember { mutableStateOf<String?>(null) }
  var editing by remember { mutableStateOf<Tag?>(null) }
  var editingName by remember { mutableStateOf("") }

  val articleCounts = remember(bookmarks, hiddenArticleIds) {
    countArticlesByTag(bookmarks, hiddenArticleIds)
  }
  val selectedTag = tags.firstOrNull { it.id == selectedTagId }
  val selectedBookmarks = remember(bookmarks, selectedTagId, hiddenArticleIds) {
    selectedTagId?.let { articlesWithTag(bookmarks, it, hiddenArticleIds) }.orEmpty()
  }

  Column(
    modifier = modifier.fillMaxSize(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
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
      return@Column
    }

    LazyRow(
      modifier = Modifier.fillMaxWidth(),
      contentPadding = PaddingValues(horizontal = 16.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      items(tags, key = Tag::id) { tag ->
        FilterChip(
          selected = tag.id == selectedTagId,
          onClick = { selectedTagId = tag.id },
          label = { Text("${tag.name} (${articleCounts[tag.id] ?: 0})") },
        )
      }
    }

    if (selectedTag == null) {
      Box(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          "タグを選択すると記事を表示します",
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    } else {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          "${selectedTag.name}  ${selectedBookmarks.size}件",
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.weight(1f),
        )
        IconButton(
          onClick = {
            editing = selectedTag
            editingName = selectedTag.name
          },
        ) {
          Icon(Icons.Default.Edit, "タグ名を変更")
        }
        IconButton(
          onClick = {
            selectedTagId = null
            onDelete(selectedTag)
          },
        ) {
          Icon(Icons.Default.Delete, "タグを削除")
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
