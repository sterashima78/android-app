package dev.terashima.yomitorirss.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.terashima.yomitorirss.feature.settings.AiModelStatus

@Composable
internal fun ModelManagerDialog(
  supported: Boolean,
  models: List<AiModelStatus>,
  progressModelId: String?,
  progressText: String?,
  onDismiss: () -> Unit,
  onDownload: (String) -> Unit,
  onSelect: (String) -> Unit,
  onDelete: (String) -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("要約モデル") },
    text = {
      LazyColumn(modifier = Modifier.fillMaxWidth()) {
        if (!supported) item { Text("この端末はarm64・4 GB以上のメモリという実行条件を満たしていません。") }
        items(models, key = AiModelStatus::id) { model ->
          Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Column(Modifier.weight(1f)) {
                Text(model.name, fontWeight = FontWeight.SemiBold)
                Text(model.description, style = MaterialTheme.typography.bodySmall)
                Text(
                  "${formatBytes(model.sizeBytes)} · ${model.quantization}${if (model.memoryLow) " · メモリ不足の可能性" else ""}",
                  style = MaterialTheme.typography.labelSmall,
                  color = if (model.memoryLow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              if (model.selected) Icon(Icons.Default.Check, "選択中", tint = MaterialTheme.colorScheme.secondary)
            }
            if (progressModelId == model.id && progressText != null) {
              Spacer(Modifier.height(8.dp))
              LinearProgressIndicator(Modifier.fillMaxWidth())
              Text(progressText, style = MaterialTheme.typography.labelSmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              when {
                !model.downloaded -> OutlinedButton(onClick = { onDownload(model.id) }, enabled = supported) {
                  Text("ダウンロード")
                }
                !model.selected -> OutlinedButton(onClick = { onSelect(model.id) }) { Text("選択") }
              }
              if (model.downloaded) TextButton(onClick = { onDelete(model.id) }) { Text("削除") }
            }
          }
          HorizontalDivider()
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
  )
}

internal fun progressLabel(stage: String, modelName: String?): String = when (stage) {
  "preparing_model" -> "${modelName.orEmpty()}を読み込んでいます"
  "generating_summary" -> "${modelName.orEmpty()}で要約を生成しています"
  else -> stage
}

private fun formatBytes(bytes: Long): String = when {
  bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes.toDouble() / (1024L * 1024 * 1024))
  bytes >= 1024L * 1024 -> "%.0f MB".format(bytes.toDouble() / (1024L * 1024))
  else -> "$bytes B"
}
