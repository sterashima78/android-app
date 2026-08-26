package dev.terashima.yomitorirss.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun WebLibrarySettingsWithExtractorBottomSheet() {
  val settings = LocalWebLibrarySettingsUiBinding.current ?: return
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
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text("Web 蔵書", style = MaterialTheme.typography.titleMedium)
    Text(
      "Web 蔵書の不足 metadata 再取得と、サイト別のタイトル・サムネイル取得ルールを管理します。",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    extractorBinding?.let { binding ->
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            enabled = !extractorBusy && !settings.refreshState.running,
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
              enabled = !extractorBusy && !settings.refreshState.running,
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
              enabled = !extractorBusy && !settings.refreshState.running,
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

    HorizontalDivider()

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text("metadata 再取得")
        Text(
          "タイトルまたは表紙が未取得の ${settings.books.size} 冊が対象です。直近の実行結果は各蔵書の下に表示します。",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          "アプリが前面にない間は WebView 取得を待機し、前面へ戻ると続きから再開します。",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Button(
        onClick = settings.onRefreshAll,
        enabled = settings.books.isNotEmpty() && !settings.refreshState.running && !extractorBusy,
      ) {
        Text("一括再取得")
      }
    }

    WebLibrarySettingsRefreshProgress(settings.refreshState)

    if (settings.books.isEmpty()) {
      Text(
        "タイトルと表紙が取得済みです。再取得が必要な Web 蔵書はありません。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      settings.books.forEach { book ->
        val result = settings.refreshState.items.firstOrNull { it.sourceId == book.sourceId }
        Card(modifier = Modifier.fillMaxWidth()) {
          Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(
              text = book.title,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
              style = MaterialTheme.typography.bodyMedium,
            )
            Text(
              text = "未取得: ${book.missingWebMetadataLabels().joinToString("・")}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
            )
            WebLibraryThumbnailPreview(book = book)
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.End,
            ) {
              TextButton(
                onClick = { settings.onRefresh(book) },
                enabled = !settings.refreshState.running && !extractorBusy,
              ) {
                Text("再取得")
              }
            }
            result?.let { WebLibrarySettingsRefreshResultText(it) }
          }
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

@Composable
private fun WebLibrarySettingsRefreshProgress(state: WebLibraryRefreshUiState) {
  if (state.total <= 0) return
  val succeeded = state.items.count {
    it.status == WebLibraryRefreshItemStatus.UPDATED ||
      it.status == WebLibraryRefreshItemStatus.UNCHANGED ||
      it.status == WebLibraryRefreshItemStatus.WARNING
  }
  val warnings = state.items.count { it.status == WebLibraryRefreshItemStatus.WARNING }
  val failures = state.items.count { it.status == WebLibraryRefreshItemStatus.FAILED }

  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    if (state.running) {
      LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
      Text(
        "再取得中 ${state.completed} / ${state.total}",
        style = MaterialTheme.typography.bodySmall,
      )
    } else {
      Text(
        "完了 ${state.completed} / ${state.total} ・ 成功 $succeeded ・ 注意 $warnings ・ 失敗 $failures",
        style = MaterialTheme.typography.bodySmall,
      )
    }
  }
}

@Composable
private fun WebLibrarySettingsRefreshResultText(result: WebLibraryRefreshItemUiState) {
  val label = when (result.status) {
    WebLibraryRefreshItemStatus.PENDING -> "待機中"
    WebLibraryRefreshItemStatus.RUNNING -> "取得中"
    WebLibraryRefreshItemStatus.UPDATED -> "成功・更新あり"
    WebLibraryRefreshItemStatus.UNCHANGED -> "成功・変更なし"
    WebLibraryRefreshItemStatus.WARNING -> "成功・要確認"
    WebLibraryRefreshItemStatus.FAILED -> "失敗"
  }
  Text(
    text = result.detail?.let { "$label: $it" } ?: label,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}
