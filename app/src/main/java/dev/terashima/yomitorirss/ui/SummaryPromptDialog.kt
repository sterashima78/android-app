package dev.terashima.yomitorirss.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
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
import dev.terashima.yomitorirss.feature.summary.SUMMARY_ARTICLE_PLACEHOLDER
import dev.terashima.yomitorirss.feature.summary.SUMMARY_PROMPT_MAX_LENGTH

@Composable
internal fun SummaryPromptDialog(
  prompt: String,
  onDismiss: () -> Unit,
  onSave: (String) -> Unit,
  onReset: () -> Unit,
) {
  var value by remember(prompt) { mutableStateOf(prompt) }
  val valid = value.isNotBlank() && value.length <= SUMMARY_PROMPT_MAX_LENGTH

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("要約プロンプト") },
    text = {
      Column {
        Text(
          "$SUMMARY_ARTICLE_PLACEHOLDER の位置に記事本文が入ります。省略した場合は末尾に本文を追加します。",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
          value = value,
          onValueChange = { value = it },
          label = { Text("プロンプト") },
          minLines = 8,
          maxLines = 14,
          supportingText = { Text("${value.length} / $SUMMARY_PROMPT_MAX_LENGTH") },
          isError = value.length > SUMMARY_PROMPT_MAX_LENGTH,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      TextButton(onClick = { onSave(value) }, enabled = valid) { Text("保存") }
    },
    dismissButton = {
      Row {
        TextButton(onClick = onReset) { Text("既定に戻す") }
        TextButton(onClick = onDismiss) { Text("キャンセル") }
      }
    },
  )
}
