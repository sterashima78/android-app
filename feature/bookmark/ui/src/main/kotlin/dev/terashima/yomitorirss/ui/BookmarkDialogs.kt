package dev.terashima.yomitorirss.feature.bookmark

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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

@Composable
fun ArticleFolderDialog(
  article: Article,
  bookmarkDetails: BookmarkedArticle?,
  folders: List<BookmarkFolder>,
  onDismiss: () -> Unit,
  onSave: (String?) -> Unit,
) {
  var selectedFolderId by remember(article.id, bookmarkDetails, folders) {
    mutableStateOf(bookmarkDetails?.folder?.id)
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("フォルダを移動") },
    text = {
      Column {
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
          item(key = "uncategorized") {
            FolderChoiceRow(
              name = "未分類",
              selected = selectedFolderId == null,
              onSelect = { selectedFolderId = null },
            )
          }
          items(folders, key = BookmarkFolder::id) { folder ->
            FolderChoiceRow(
              name = folder.name,
              selected = selectedFolderId == folder.id,
              onSelect = { selectedFolderId = folder.id },
            )
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = { onSave(selectedFolderId) }) {
        Text("移動")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("キャンセル")
      }
    },
  )
}

@Composable
fun ArticleTagsDialog(
  article: Article,
  bookmarkDetails: BookmarkedArticle?,
  tags: List<Tag>,
  onDismiss: () -> Unit,
  onSave: (Set<String>) -> Unit,
) {
  var selected by remember(article.id, bookmarkDetails, tags) {
    mutableStateOf(bookmarkDetails?.tags.orEmpty().map(Tag::id).toSet())
  }
  var query by remember(article.id, tags) { mutableStateOf("") }
  val visibleTags = remember(tags, query) {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) tags else tags.filter { it.name.contains(normalizedQuery, ignoreCase = true) }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("タグを編集") },
    text = {
      Column {
        if (tags.isEmpty()) {
          Text("タグがありません。先にタグ管理から作成してください。")
        } else {
          OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("タグを検索") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          if (visibleTags.isEmpty()) {
            Text("一致するタグがありません。")
          } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
              items(visibleTags, key = Tag::id) { tag ->
                Row(
                  modifier = Modifier.fillMaxWidth().clickable {
                    selected = if (tag.id in selected) selected - tag.id else selected + tag.id
                  },
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Checkbox(
                    checked = tag.id in selected,
                    onCheckedChange = { checked ->
                      selected = if (checked) selected + tag.id else selected - tag.id
                    },
                  )
                  Text(tag.name)
                }
              }
            }
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = { onSave(selected) }) { Text("保存") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
  )
}

@Composable
private fun FolderChoiceRow(
  name: String,
  selected: Boolean,
  onSelect: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    RadioButton(selected = selected, onClick = onSelect)
    Text(name)
  }
}
