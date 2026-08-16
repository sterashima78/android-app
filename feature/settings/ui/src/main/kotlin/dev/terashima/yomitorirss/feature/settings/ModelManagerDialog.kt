package dev.terashima.yomitorirss.feature.settings

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ModelManagerDialog(
  supported: Boolean,
  models: List<AiModelStatus>,
  inferenceBackend: AiInferenceBackend,
  thinkingEnabled: Boolean,
  speculativeDecodingEnabled: Boolean,
  progressModelId: String?,
  progressText: String?,
  onDismiss: () -> Unit,
  onBackendChange: (AiInferenceBackend) -> Unit,
  onThinkingChange: (Boolean) -> Unit,
  onSpeculativeDecodingChange: (Boolean) -> Unit,
  onDownload: (String) -> Unit,
  onSelect: (String) -> Unit,
  onDelete: (String) -> Unit,
) {
  val selectedModel = models.firstOrNull(AiModelStatus::selected)

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("AIモデル") },
    text = {
      LazyColumn(modifier = Modifier.fillMaxWidth()) {
        if (!supported) item { Text("この端末はarm64・4 GB以上のメモリという実行条件を満たしていません。") }
        item {
          Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
            Text("実行デバイス", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              FilterChip(
                selected = inferenceBackend == AiInferenceBackend.CPU,
                onClick = { onBackendChange(AiInferenceBackend.CPU) },
                enabled = supported,
                label = { Text("CPU") },
              )
              FilterChip(
                selected = inferenceBackend == AiInferenceBackend.GPU,
                onClick = { onBackendChange(AiInferenceBackend.GPU) },
                enabled = supported,
                label = { Text("GPU") },
              )
            }
            Text(
              "GPUは対応端末で推論を高速化できます。端末やモデルによっては利用できない場合があります。",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (selectedModel?.supportsSpeculativeDecoding == true) {
              Spacer(Modifier.height(12.dp))
              Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                  Text("Speculative decoding", fontWeight = FontWeight.SemiBold)
                  Text(
                    "複数トークンを先読みして検証し、対応する処理では生成を高速化します。効果は端末や処理内容によって異なります。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Switch(
                  checked = speculativeDecodingEnabled,
                  onCheckedChange = onSpeculativeDecodingChange,
                  enabled = supported && selectedModel.downloaded,
                )
              }
            }
            if (selectedModel?.supportsThinking == true) {
              Spacer(Modifier.height(12.dp))
              Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                  Text("Thinking", fontWeight = FontWeight.SemiBold)
                  Text(
                    "推論を強化します。応答時間と消費電力は増える場合があります。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Switch(
                  checked = thinkingEnabled,
                  onCheckedChange = onThinkingChange,
                  enabled = supported,
                )
              }
            }
          }
          HorizontalDivider()
        }
        items(models, key = AiModelStatus::id) { model ->
          Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Column(Modifier.weight(1f)) {
                Text(model.name, fontWeight = FontWeight.SemiBold)
                Text(model.description, style = MaterialTheme.typography.bodySmall)
                Text(
                  "${formatBytes(model.sizeBytes)} · ${model.quantization}${if (model.supportsSpeculativeDecoding) " · Speculative decoding対応" else ""}${if (model.supportsThinking) " · Thinking対応" else ""}${if (model.memoryLow) " · メモリ不足の可能性" else ""}",
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

private fun formatBytes(bytes: Long): String = when {
  bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes.toDouble() / (1024L * 1024 * 1024))
  bytes >= 1024L * 1024 -> "%.0f MB".format(bytes.toDouble() / (1024L * 1024))
  else -> "$bytes B"
}
