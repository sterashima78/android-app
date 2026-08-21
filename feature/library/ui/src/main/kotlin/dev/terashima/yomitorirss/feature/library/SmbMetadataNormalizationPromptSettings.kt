package dev.terashima.yomitorirss.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import androidx.compose.ui.unit.dp

@Composable
internal fun SmbMetadataNormalizationPromptSettingsSection(
  prompt: String,
  editable: Boolean,
  onSave: (String) -> Unit,
  onReset: () -> Unit,
) {
  var editorVisible by remember { mutableStateOf(false) }
  var draft by remember(prompt) { mutableStateOf(prompt) }

  if (editorVisible) {
    AlertDialog(
      onDismissRequest = { editorVisible = false },
      title = { Text("書誌正規化プロンプト") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            "現在のファイル名を差し込みたい位置では $SMB_METADATA_NORMALIZATION_FILE_NAME_PLACEHOLDER を使用できます。省略した場合は末尾に自動追加します。出力ツールと書誌スキーマの検証規則は固定です。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("プロンプト") },
            minLines = 10,
            maxLines = 18,
            supportingText = {
              Text("${draft.length} / $SMB_METADATA_NORMALIZATION_PROMPT_MAX_LENGTH 文字")
            },
          )
        }
      },
      confirmButton = {
        TextButton(
          enabled = draft.isNotBlank() && draft.length <= SMB_METADATA_NORMALIZATION_PROMPT_MAX_LENGTH,
          onClick = {
            onSave(draft)
            editorVisible = false
          },
        ) { Text("保存") }
      },
      dismissButton = {
        TextButton(onClick = { editorVisible = false }) { Text("キャンセル") }
      },
    )
  }

  Card(Modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text("解析プロンプト", style = MaterialTheme.typography.titleSmall)
        Text(
          "表紙と現在のファイル名から書誌候補を生成するときの指示を調整できます。",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(
          enabled = editable,
          onClick = {
            draft = prompt
            editorVisible = true
          },
        ) { Text("編集") }
        TextButton(
          enabled = editable && prompt != DEFAULT_SMB_METADATA_NORMALIZATION_PROMPT,
          onClick = onReset,
        ) { Text("既定値に戻す") }
      }
    }
  }
}