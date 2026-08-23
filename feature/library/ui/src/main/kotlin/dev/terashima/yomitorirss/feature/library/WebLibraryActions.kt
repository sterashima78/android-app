package dev.terashima.yomitorirss.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun WebLibraryActions(
  books: List<LibraryBook>,
  onAdd: (String) -> Unit,
  onRefresh: (LibraryBook) -> Unit,
  onRefreshAll: () -> Unit,
  refreshingSourceIds: Set<String>,
  onMoveToBookmark: (LibraryBook) -> Unit,
  modifier: Modifier = Modifier,
) {
  var visible by remember { mutableStateOf(false) }
  var url by remember { mutableStateOf("") }
  val refreshing = refreshingSourceIds.isNotEmpty()

  FloatingActionButton(
    modifier = modifier,
    onClick = { visible = true },
  ) {
    Icon(Icons.Default.Add, contentDescription = "Web蔵書を追加")
  }

  if (!visible) return

  AlertDialog(
    onDismissRequest = { visible = false },
    title = { Text("Web蔵書") },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 520.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        OutlinedTextField(
          value = url,
          onValueChange = { url = it },
          modifier = Modifier.fillMaxWidth(),
          label = { Text("URL") },
          placeholder = { Text("https://example.com/book") },
          singleLine = true,
        )
        Button(
          onClick = {
            val normalized = url.trim()
            if (normalized.isNotEmpty()) {
              onAdd(normalized)
              url = ""
            }
          },
          enabled = url.isNotBlank() && !refreshing,
        ) {
          Text("URLから追加")
        }

        if (books.isNotEmpty()) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Webから追加した蔵書", modifier = Modifier.weight(1f))
            TextButton(
              onClick = onRefreshAll,
              enabled = !refreshing,
            ) {
              Text("すべて再取得")
            }
          }
          if (refreshing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
          }
          books.forEach { book ->
            val bookRefreshing = book.sourceId in refreshingSourceIds
            Row(
              modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = book.title,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
              TextButton(
                onClick = { onRefresh(book) },
                enabled = !refreshing,
              ) {
                Text(if (bookRefreshing) "取得中" else "再取得")
              }
              TextButton(
                onClick = { onMoveToBookmark(book) },
                enabled = !refreshing,
              ) {
                Text("ブックマークへ移動")
              }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = { visible = false }) {
        Text("閉じる")
      }
    },
  )
}
