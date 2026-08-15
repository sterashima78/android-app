package dev.terashima.yomitorirss.feature.bookmark

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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

@Composable
internal fun FolderManagerScreen(
  folders: List<BookmarkFolder>,
  onCreate: (String) -> Unit,
  onRename: (BookmarkFolder, String) -> Unit,
  onDelete: (BookmarkFolder) -> Unit,
  modifier: Modifier = Modifier,
) {
  var newName by remember { mutableStateOf("") }
  var editing by remember { mutableStateOf<BookmarkFolder?>(null) }
  var editingName by remember { mutableStateOf("") }

  Column(
    modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      OutlinedTextField(
        value = newName,
        onValueChange = { newName = it },
        label = { Text("新しいフォルダ") },
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

    LazyColumn(
      modifier = Modifier.weight(1f),
      contentPadding = PaddingValues(bottom = 16.dp),
    ) {
      item(key = "uncategorized") {
        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
          Text("未分類")
          Text(
            "フォルダ未設定のブックマークは自動的にここへ表示されます",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        HorizontalDivider()
      }

      if (folders.isEmpty()) {
        item {
          Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text("作成したフォルダはありません", color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      } else {
        items(folders, key = BookmarkFolder::id) { folder ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(
              modifier = Modifier
                .weight(1f)
                .let { base ->
                  if (folder.isSystem) base else base.clickable {
                    editing = folder
                    editingName = folder.name
                  }
                }
                .padding(vertical = 14.dp),
            ) {
              Text(folder.name)
              if (folder.isSystem) {
                Text(
                  "システムフォルダ",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            if (!folder.isSystem) {
              IconButton(onClick = { onDelete(folder) }) {
                Icon(Icons.Default.Delete, "削除")
              }
            }
          }
          HorizontalDivider()
        }
      }
    }
  }

  editing?.let { folder ->
    AlertDialog(
      onDismissRequest = { editing = null },
      title = { Text("フォルダ名を変更") },
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
            onRename(folder, editingName)
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
