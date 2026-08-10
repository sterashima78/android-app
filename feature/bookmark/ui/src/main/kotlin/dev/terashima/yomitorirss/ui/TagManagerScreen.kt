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
internal fun TagManagerScreen(
  tags: List<Tag>,
  onCreate: (String) -> Unit,
  onRename: (Tag, String) -> Unit,
  onDelete: (Tag) -> Unit,
  modifier: Modifier = Modifier,
) {
  var newName by remember { mutableStateOf("") }
  var editing by remember { mutableStateOf<Tag?>(null) }
  var editingName by remember { mutableStateOf("") }

  Column(
    modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
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
    } else {
      LazyColumn(
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(bottom = 16.dp),
      ) {
        items(tags, key = Tag::id) { tag ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              tag.name,
              modifier = Modifier
                .weight(1f)
                .clickable {
                  editing = tag
                  editingName = tag.name
                }
                .padding(vertical = 14.dp),
            )
            IconButton(onClick = { onDelete(tag) }) {
              Icon(Icons.Default.Delete, "削除")
            }
          }
          HorizontalDivider()
        }
      }
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
