package dev.terashima.yomitorirss.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun WebLibrarySettingsWithExtractorBottomSheet() {
  val extractorBinding = LocalWebLibraryMetadataExtractorUiBinding.current
  var extractorRules by remember { mutableStateOf(emptyList<WebLibraryMetadataExtractor>()) }
  var editingExtractor by remember { mutableStateOf<WebLibraryMetadataExtractor?>(null) }
  var creatingExtractor by remember { mutableStateOf(false) }
  var extractorBusy by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()

  fun reloadExtractorRules() {
    val binding = extractorBinding ?: return
    scope.launch {
      extractorBusy = true
      runCatching { withContext(Dispatchers.IO) { binding.list() } }
        .onSuccess { extractorRules = it }
        .onFailure(binding.onError)
      extractorBusy = false
    }
  }

  LaunchedEffect(extractorBinding) {
    if (extractorBinding != null) reloadExtractorRules()
  }

  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(androidx.compose.ui.unit.dp(12f)),
  ) {
    Text("Web 蔵書", style = MaterialTheme.typography.titleMedium)
    Text(
      "Web 蔵書のサイト別のタイトル・サムネイル取得ルールを管理します。metadata の再取得は蔵書一覧から行えます。",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    extractorBinding?.let { binding ->
      Column(verticalArrangement = Arrangement.spacedBy(androidx.compose.ui.unit.dp(8f))) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("タイトル・サムネイル取得ルール")
            Text(
              "URL パターンに一致したページでは、専用 WebView 内で登録した非同期関数を実行します。",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          TextButton(
            onClick = { creatingExtractor = true },
            enabled = !extractorBusy,
          ) {
            Text("追加")
          }
        }
        if (extractorBusy) {
          LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        extractorRules.forEach { extractor ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = extractor.urlPattern,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
              )
              Text(
                text = "WebView タイムアウト ${extractor.timeoutSeconds} 秒",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            TextButton(
              onClick = { editingExtractor = extractor },
              enabled = !extractorBusy,
            ) {
              Text("編集")
            }
            TextButton(
              onClick = {
                scope.launch {
                  extractorBusy = true
                  runCatching {
                    withContext(Dispatchers.IO) {
                      binding.delete(extractor.id)
                      binding.list()
                    }
                  }
                    .onSuccess { extractorRules = it }
                    .onFailure(binding.onError)
                  extractorBusy = false
                }
              },
              enabled = !extractorBusy,
            ) {
              Text("削除")
            }
          }
        }
        if (!extractorBusy && extractorRules.isEmpty()) {
          Text(
            "登録済みの取得ルールはありません。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }

  val editorExtractor = editingExtractor
  val binding = extractorBinding
  if ((creatingExtractor || editorExtractor != null) && binding != null) {
    WebLibraryMetadataExtractorEditorBottomSheet(
      extractor = editorExtractor,
      busy = extractorBusy,
      onDismiss = {
        creatingExtractor = false
        editingExtractor = null
      },
      onSave = { id, urlPattern, functionCode, timeoutSeconds ->
        scope.launch {
          extractorBusy = true
          runCatching {
            withContext(Dispatchers.IO) {
              binding.save(id, urlPattern, functionCode, timeoutSeconds)
              binding.list()
            }
          }
            .onSuccess { updated ->
              extractorRules = updated
              creatingExtractor = false
              editingExtractor = null
            }
            .onFailure(binding.onError)
          extractorBusy = false
        }
      },
      onTest = binding.test,
      onError = binding.onError,
    )
  }
}
