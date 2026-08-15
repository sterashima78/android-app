package dev.terashima.yomitorirss.feature.rss

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AddFeedDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
  var value by remember { mutableStateOf("") }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("フィードを追加") },
    text = {
      OutlinedTextField(
        value = value,
        onValueChange = { value = it },
        label = { Text("RSSまたはWebサイトURL") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
    },
    confirmButton = { TextButton(onClick = { onAdd(value) }, enabled = value.isNotBlank()) { Text("追加") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
  )
}

@Composable
fun CandidateDialog(
  candidates: List<FeedCandidate>,
  onDismiss: () -> Unit,
  onSelect: (FeedCandidate) -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("フィードを選択") },
    text = {
      Column {
        candidates.forEach { candidate ->
          Column(Modifier.fillMaxWidth().clickable { onSelect(candidate) }.padding(vertical = 10.dp)) {
            Text(candidate.title, fontWeight = FontWeight.Medium)
            Text(candidate.url, style = MaterialTheme.typography.bodySmall, maxLines = 2)
          }
          HorizontalDivider()
        }
      }
    },
    confirmButton = {},
    dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
  )
}
