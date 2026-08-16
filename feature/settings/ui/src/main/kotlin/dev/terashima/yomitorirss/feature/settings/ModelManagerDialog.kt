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
  contextSizeMode: AiContextSizeMode,
  effectiveContextTokens: Int?,
  benchmarkRunning: Boolean,
  benchmarkResult: AiModelBenchmarkComparison?,
  benchmarkError: String?,
  contextBenchmarkResult: AiContextBenchmarkReport?,
  contextBenchmarkError: String?,
  progressModelId: String?,
  progressText: String?,
  onDismiss: () -> Unit,
  onBackendChange: (AiInferenceBackend) -> Unit,
  onThinkingChange: (Boolean) -> Unit,
  onSpeculativeDecodingChange: (Boolean) -> Unit,
  onContextSizeChange: (AiContextSizeMode) -> Unit,
  onRunBenchmark: () -> Unit,
  onRunContextBenchmark: () -> Unit,
  onDownload: (String) -> Unit,
  onSelect: (String) -> Unit,
  onDelete: (String) -> Unit,
) {
  val selectedModel = models.firstOrNull(AiModelStatus::selected)

  AlertDialog(
    onDismissRequest = { if (!benchmarkRunning) onDismiss() },
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
                enabled = supported && !benchmarkRunning,
                label = { Text("CPU") },
              )
              FilterChip(
                selected = inferenceBackend == AiInferenceBackend.GPU,
                onClick = { onBackendChange(AiInferenceBackend.GPU) },
                enabled = supported && !benchmarkRunning,
                label = { Text("GPU") },
              )
            }
            Text(
              "GPUは対応端末で推論を高速化できます。端末やモデルによっては利用できない場合があります。",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))
            Text("コンテキストサイズ", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              ContextChip(
                label = "自動",
                mode = AiContextSizeMode.AUTO,
                selectedMode = contextSizeMode,
                enabled = supported && !benchmarkRunning,
                onChange = onContextSizeChange,
              )
              ContextChip(
                label = "4K",
                mode = AiContextSizeMode.CONTEXT_4K,
                selectedMode = contextSizeMode,
                enabled = supported && !benchmarkRunning,
                onChange = onContextSizeChange,
              )
              ContextChip(
                label = "8K",
                mode = AiContextSizeMode.CONTEXT_8K,
                selectedMode = contextSizeMode,
                enabled = supported && !benchmarkRunning,
                onChange = onContextSizeChange,
              )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              ContextChip(
                label = "16K",
                mode = AiContextSizeMode.CONTEXT_16K,
                selectedMode = contextSizeMode,
                enabled = supported && !benchmarkRunning,
                onChange = onContextSizeChange,
              )
              ContextChip(
                label = "32K",
                mode = AiContextSizeMode.CONTEXT_32K,
                selectedMode = contextSizeMode,
                enabled = supported && !benchmarkRunning,
                onChange = onContextSizeChange,
              )
            }
            Text(
              buildString {
                append("EngineのKV cache上限です。")
                effectiveContextTokens?.let { append(" 現在は${formatContextTokens(it)}。") }
                if (contextSizeMode == AiContextSizeMode.AUTO) {
                  append(" 自動は実機ベンチマークの安全な最大値を使い、未計測時は8Kです。")
                }
              },
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
                  enabled = supported && selectedModel.downloaded && !benchmarkRunning,
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
                  enabled = supported && !benchmarkRunning,
                )
              }
            }
            if (selectedModel?.downloaded == true) {
              Spacer(Modifier.height(16.dp))
              Text("コンテキストベンチマーク", fontWeight = FontWeight.SemiBold)
              Text(
                "4Kから順にコンテキストの約75%をprefillし、プロセスのピークPSS・Native/Graphics PSS・最低空きメモリと処理時間を計測します。余裕が不足した時点でより大きい設定は試しません。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Spacer(Modifier.height(8.dp))
              OutlinedButton(
                onClick = onRunContextBenchmark,
                enabled = supported && !benchmarkRunning,
              ) {
                Text(if (benchmarkRunning) "計測中…" else "コンテキストを計測")
              }
              contextBenchmarkResult?.let {
                Spacer(Modifier.height(12.dp))
                ContextBenchmarkResult(result = it)
              }
              contextBenchmarkError?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                  "コンテキスト計測に失敗しました: $it",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.error,
                )
              }

              Spacer(Modifier.height(16.dp))
              Text("生成性能ベンチマーク", fontWeight = FontWeight.SemiBold)
              Text(
                "選択中の${inferenceBackend.name}で、要約を想定した prefill 2048 / decode 128 トークンを標準→Speculativeの順に計測します。実行中は他のバックグラウンドAIタスクを待機させます。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Spacer(Modifier.height(8.dp))
              OutlinedButton(
                onClick = onRunBenchmark,
                enabled = supported && !benchmarkRunning,
              ) {
                Text(if (benchmarkRunning) "計測中…" else "生成性能を計測")
              }
              if (benchmarkRunning) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                  "AIベンチマークを実行しています。",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              benchmarkResult?.let {
                Spacer(Modifier.height(12.dp))
                BenchmarkResult(result = it)
              }
              benchmarkError?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                  "ベンチマークに失敗しました: $it",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.error,
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
                  "${formatBytes(model.sizeBytes)} · ${model.quantization}${if (model.selected) " · context ${formatContextTokens(model.contextTokens)}" else ""}${if (model.supportsSpeculativeDecoding) " · Speculative decoding対応" else ""}${if (model.supportsThinking) " · Thinking対応" else ""}${if (model.memoryLow) " · メモリ不足の可能性" else ""}",
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
                !model.downloaded -> OutlinedButton(
                  onClick = { onDownload(model.id) },
                  enabled = supported && !benchmarkRunning,
                ) {
                  Text("ダウンロード")
                }
                !model.selected -> OutlinedButton(
                  onClick = { onSelect(model.id) },
                  enabled = !benchmarkRunning,
                ) {
                  Text("選択")
                }
              }
              if (model.downloaded) {
                TextButton(
                  onClick = { onDelete(model.id) },
                  enabled = !benchmarkRunning,
                ) {
                  Text("削除")
                }
              }
            }
          }
          HorizontalDivider()
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss, enabled = !benchmarkRunning) { Text("閉じる") }
    },
  )
}

@Composable
private fun ContextChip(
  label: String,
  mode: AiContextSizeMode,
  selectedMode: AiContextSizeMode,
  enabled: Boolean,
  onChange: (AiContextSizeMode) -> Unit,
) {
  FilterChip(
    selected = mode == selectedMode,
    onClick = { onChange(mode) },
    enabled = enabled,
    label = { Text(label) },
  )
}

@Composable
private fun ContextBenchmarkResult(result: AiContextBenchmarkReport) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(
      "自動推奨: ${formatContextTokens(result.recommendedContextTokens)} · ${result.backend.name}${if (result.speculativeDecodingEnabled) " · Speculative" else ""}",
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.SemiBold,
    )
    result.samples.forEach { sample ->
      Column {
        Text(
          "${formatContextTokens(sample.contextTokens)} · ${if (sample.safe) "安全圏" else "要注意"}",
          style = MaterialTheme.typography.bodySmall,
          fontWeight = FontWeight.SemiBold,
          color = if (sample.safe) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
        )
        if (sample.succeeded) {
          Text(
            "Peak PSS ${formatBytes(sample.peakPssBytes)} · Native ${formatBytes(sample.peakNativePssBytes)} · Graphics ${formatBytes(sample.peakGraphicsPssBytes)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            "最低空き ${formatBytes(sample.minimumAvailableMemoryBytes)} · prefill ${sample.requestedPrefillTokens} tok · 初期化 ${formatDuration(sample.initTimeMillis)} · 推論 ${formatDuration(sample.inferenceTimeMillis)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          Text(
            sample.error.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
      }
    }
    Text(
      "自動設定は、最低空きメモリを端末RAMの15%以上かつ1 GB以上残し、Peak PSSを端末RAMの70%以下に保てた最大値を採用します。",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun BenchmarkResult(result: AiModelBenchmarkComparison) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(
      "${result.modelName} · ${result.backend.name} · prefill ${result.requestedPrefillTokens} / decode ${result.requestedDecodeTokens}",
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.SemiBold,
    )
    BenchmarkSample(label = "標準", sample = result.standard)
    result.speculative?.let { sample ->
      BenchmarkSample(label = "Speculative", sample = sample)
      val decodeSpeedup = result.decodeSpeedup
      val totalSpeedup = result.totalTimeSpeedup
      if (decodeSpeedup != null || totalSpeedup != null) {
        Text(
          buildString {
            append("比較: ")
            decodeSpeedup?.let { append("Decode ${formatMultiplier(it)}") }
            if (decodeSpeedup != null && totalSpeedup != null) append(" · ")
            totalSpeedup?.let { append("総時間 ${formatMultiplier(it)}") }
          },
          style = MaterialTheme.typography.bodySmall,
          fontWeight = FontWeight.SemiBold,
        )
      }
    }
    result.speculativeError?.let { error ->
      Text(
        "Speculative計測失敗: $error",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
      )
    }
    Text(
      "1.0×より大きいほどSpeculative decodingが高速です。端末温度や他処理の影響を受けるため、必要に応じて複数回比較してください。",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun BenchmarkSample(label: String, sample: AiModelBenchmarkSample) {
  Column {
    Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    Text(
      "Decode ${formatRate(sample.decodeTokensPerSecond)} tok/s · Prefill ${formatRate(sample.prefillTokensPerSecond)} tok/s",
      style = MaterialTheme.typography.bodySmall,
    )
    Text(
      "TTFT ${formatDuration(sample.timeToFirstTokenMillis)} · 初期化 ${formatDuration(sample.initTimeMillis)} · 総時間 ${formatDuration(sample.totalTimeMillis)}",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

private fun formatBytes(bytes: Long): String = when {
  bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes.toDouble() / (1024L * 1024 * 1024))
  bytes >= 1024L * 1024 -> "%.0f MB".format(bytes.toDouble() / (1024L * 1024))
  else -> "$bytes B"
}

private fun formatContextTokens(tokens: Int): String = when (tokens) {
  4_096 -> "4K"
  8_192 -> "8K"
  16_384 -> "16K"
  32_768 -> "32K"
  else -> "$tokens"
}

private fun formatRate(value: Double): String = "%.1f".format(value)
private fun formatMultiplier(value: Double): String = "%.2f×".format(value)
private fun formatDuration(millis: Long): String = if (millis < 1_000) {
  "$millis ms"
} else {
  "%.2f s".format(millis / 1000.0)
}
