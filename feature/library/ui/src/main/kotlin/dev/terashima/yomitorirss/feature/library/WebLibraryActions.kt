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
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class WebLibraryRefreshItemStatus {
  PENDING,
  RUNNING,
  UPDATED,
  UNCHANGED,
  WARNING,
  FAILED,
}

internal data class WebLibraryRefreshItemUiState(
  val sourceId: String,
  val title: String,
  val status: WebLibraryRefreshItemStatus,
  val detail: String? = null,
)

internal data class WebLibraryRefreshUiState(
  val running: Boolean = false,
  val total: Int = 0,
  val completed: Int = 0,
  val items: List<WebLibraryRefreshItemUiState> = emptyList(),
)

internal data class WebLibrarySettingsUiBinding(
  val books: List<LibraryBook>,
  val refreshState: WebLibraryRefreshUiState,
  val onRefresh: (LibraryBook) -> Unit,
  val onRefreshAll: () -> Unit,
  val onMoveToBookmark: (LibraryBook) -> Unit,
)

internal val LocalWebLibrarySettingsUiBinding =
  staticCompositionLocalOf<WebLibrarySettingsUiBinding?> { null }

@Composable
fun WebLibraryAddAction(
  onAdd: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  var visible by remember { mutableStateOf(false) }
  var url by remember { mutableStateOf("") }

  FloatingActionButton(
    modifier = modifier,
    onClick = { visible = true },
  ) {
    Icon(Icons.Default.Add, contentDescription = "Web蔵書を追加")
  }

  if (visible) {
    AlertDialog(
      onDismissRequest = { visible = false },
      title = { Text("Web蔵書を追加") },
      text = {
        OutlinedTextField(
          value = url,
          onValueChange = { url = it },
          modifier = Modifier.fillMaxWidth(),
          label = { Text("URL") },
          placeholder = { Text("https://example.com/book") },
          singleLine = true,
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            val normalized = url.trim()
            if (normalized.isNotEmpty()) {
              onAdd(normalized)
              url = ""
              visible = false
            }
          },
          enabled = url.isNotBlank(),
        ) {
          Text("追加")
        }
      },
      dismissButton = {
        TextButton(onClick = { visible = false }) {
          Text("キャンセル")
        }
      },
    )
  }
}

@Composable
internal fun WebLibrarySettingsFromBinding() {
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
      "Web 蔵書の metadata 再取得と、サイト別のタイトル・サムネイル取得ルールを管理します。",
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
            Text(
              text = extractor.urlPattern,
              modifier = Modifier.weight(1f),
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
              style = MaterialTheme.typography.bodySmall,
            )
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
          "${settings.books.size} 冊の Web 蔵書が対象です。直近の実行結果は各蔵書の下に表示します。",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Button(
        onClick = settings.onRefreshAll,
        enabled = settings.books.isNotEmpty() && !settings.refreshState.running && !extractorBusy,
      ) {
        Text("すべて再取得")
      }
    }

    WebLibraryRefreshProgress(settings.refreshState)

    if (settings.books.isEmpty()) {
      Text(
        "Web から追加した蔵書はありません。",
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
              TextButton(
                onClick = { settings.onMoveToBookmark(book) },
                enabled = !settings.refreshState.running && !extractorBusy,
              ) {
                Text("ブックマークへ移動")
              }
            }
            result?.let { WebLibraryRefreshResultText(it) }
          }
        }
      }
    }
  }

  val editorExtractor = editingExtractor
  if (creatingExtractor || editorExtractor != null) {
    WebLibraryMetadataExtractorEditor(
      extractor = editorExtractor,
      busy = extractorBusy,
      onDismiss = {
        creatingExtractor = false
        editingExtractor = null
      },
      onSave = { id, urlPattern, functionCode ->
        val binding = extractorBinding
        if (binding != null) {
          scope.launch {
            extractorBusy = true
            runCatching {
              withContext(Dispatchers.IO) {
                binding.save(id, urlPattern, functionCode)
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
        }
      },
    )
  }
}

@Composable
private fun WebLibraryRefreshProgress(state: WebLibraryRefreshUiState) {
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
private fun WebLibraryRefreshResultText(result: WebLibraryRefreshItemUiState) {
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

internal fun webLibraryRefreshSuccessUiState(
  sourceId: String,
  title: String,
  result: WebLibraryMetadataRefreshResult,
): WebLibraryRefreshItemUiState {
  val extractor = result.extractorExecution
  val warning = result.fallbackReason != null ||
    (extractor != null && extractor.status != WebLibraryMetadataExtractorStatus.APPLIED)
  val status = when {
    warning -> WebLibraryRefreshItemStatus.WARNING
    result.changedFields.isEmpty() -> WebLibraryRefreshItemStatus.UNCHANGED
    else -> WebLibraryRefreshItemStatus.UPDATED
  }
  val details = buildList {
    extractor?.let { execution ->
      when (execution.status) {
        WebLibraryMetadataExtractorStatus.MATCHED -> add(
          "取得ルール「${execution.urlPattern}」に一致。カスタムスクリプト開始前に WebView 処理が終了",
        )
        WebLibraryMetadataExtractorStatus.RUNNING -> add(
          "取得ルール「${execution.urlPattern}」のカスタムスクリプトを開始。結果確定前に WebView 処理が終了",
        )
        WebLibraryMetadataExtractorStatus.APPLIED -> add(
          "取得ルール「${execution.urlPattern}」を適用（カスタム値取得成功: " +
            webLibraryExtractorValueDetail(execution) +
            "）",
        )
        else -> {
          val reason = webLibraryExtractorStatusLabel(execution.status)
          val message = execution.message?.takeIf(String::isNotBlank)?.let { ": $it" }.orEmpty()
          add("取得ルール「${execution.urlPattern}」を適用できませんでした ($reason$message)。標準取得を使用")
        }
      }
    } ?: if (result.fallbackReason == null) {
      add("登録ルールは適用されず、標準取得を使用")
    } else {
      Unit
    }
    result.fallbackReason?.let { add("WebView 取得失敗: $it。静的 metadata を使用") }
    add(
      if (result.changedFields.isEmpty()) {
        "metadata の変更なし"
      } else {
        "更新: ${result.changedFields.joinToString("・", transform = ::webLibraryMetadataFieldLabel)}"
      },
    )
  }
  return WebLibraryRefreshItemUiState(
    sourceId = sourceId,
    title = title,
    status = status,
    detail = details.joinToString(" / "),
  )
}

private fun webLibraryExtractorValueDetail(execution: WebLibraryMetadataExtractorExecution): String {
  val title = execution.extractedTitle
    ?.takeIf(String::isNotBlank)
    ?.let(::webLibraryDiagnosticValue)
    ?.let { "タイトル「$it」" }
    ?: "タイトルなし"
  val thumbnail = execution.extractedThumbnailUrl
    ?.takeIf(String::isNotBlank)
    ?.let(::webLibraryDiagnosticValue)
    ?.let { "サムネイル $it" }
    ?: "サムネイルなし"
  return "$title・$thumbnail"
}

private fun webLibraryDiagnosticValue(value: String): String {
  val normalized = value
    .replace('\n', ' ')
    .replace('\r', ' ')
    .trim()
  return if (normalized.length <= WEB_LIBRARY_DIAGNOSTIC_VALUE_MAX_LENGTH) {
    normalized
  } else {
    normalized.take(WEB_LIBRARY_DIAGNOSTIC_VALUE_MAX_LENGTH - 3) + "..."
  }
}

private fun webLibraryMetadataFieldLabel(field: WebLibraryMetadataField): String = when (field) {
  WebLibraryMetadataField.TITLE -> "タイトル"
  WebLibraryMetadataField.THUMBNAIL -> "サムネイル"
  WebLibraryMetadataField.DESCRIPTION -> "説明"
  WebLibraryMetadataField.AUTHORS -> "著者"
}

private fun webLibraryExtractorStatusLabel(status: WebLibraryMetadataExtractorStatus): String = when (status) {
  WebLibraryMetadataExtractorStatus.MATCHED -> "ルール一致"
  WebLibraryMetadataExtractorStatus.RUNNING -> "実行中"
  WebLibraryMetadataExtractorStatus.APPLIED -> "適用"
  WebLibraryMetadataExtractorStatus.EMPTY_RESULT -> "空の結果"
  WebLibraryMetadataExtractorStatus.INVALID_FUNCTION -> "関数形式が不正"
  WebLibraryMetadataExtractorStatus.NON_PROMISE_RESULT -> "Promise を返していない"
  WebLibraryMetadataExtractorStatus.REJECTED -> "Promise が reject"
  WebLibraryMetadataExtractorStatus.THREW -> "実行時エラー"
  WebLibraryMetadataExtractorStatus.TIMED_OUT -> "タイムアウト"
  WebLibraryMetadataExtractorStatus.INVALID_STATE -> "実行状態が不正"
  WebLibraryMetadataExtractorStatus.INVALID_RESULT -> "戻り値が不正"
}

@Composable
private fun WebLibraryMetadataExtractorEditor(
  extractor: WebLibraryMetadataExtractor?,
  busy: Boolean,
  onDismiss: () -> Unit,
  onSave: (String?, String, String) -> Unit,
) {
  var urlPattern by remember(extractor?.id) { mutableStateOf(extractor?.urlPattern.orEmpty()) }
  var functionCode by remember(extractor?.id) { mutableStateOf(extractor?.functionCode.orEmpty()) }

  AlertDialog(
    onDismissRequest = { if (!busy) onDismiss() },
    title = { Text(if (extractor == null) "取得ルールを追加" else "取得ルールを編集") },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 520.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text("URL パターンでは * を任意長、? を1文字のワイルドカードとして利用できます。HTTPS のみ対象です。")
        OutlinedTextField(
          value = urlPattern,
          onValueChange = { urlPattern = it },
          modifier = Modifier.fillMaxWidth(),
          label = { Text("URL パターン") },
          placeholder = { Text("https://example.com/books/*") },
          singleLine = true,
        )
        Text("関数は WebView 内で実行され、Promise<{ title, thumbnailUrl }> を返してください。async/await や fetch などの非同期処理も利用できます。値がない項目は null にできます。")
        OutlinedTextField(
          value = functionCode,
          onValueChange = { functionCode = it },
          modifier = Modifier.fillMaxWidth(),
          label = { Text("関数コード") },
          placeholder = {
            Text(
              "async () => ({ title: document.querySelector('h1')?.textContent?.trim() ?? null, " +
                "thumbnailUrl: document.querySelector('img.cover')?.currentSrc ?? null })",
            )
          },
          minLines = 7,
        )
        Text("このコードは専用 WebView profile のページコンテキストで動作します。必要な非同期処理だけを行い、不要な DOM 変更などの副作用は避けることを推奨します。")
      }
    },
    confirmButton = {
      TextButton(
        onClick = { onSave(extractor?.id, urlPattern, functionCode) },
        enabled = !busy && urlPattern.isNotBlank() && functionCode.isNotBlank(),
      ) {
        Text(if (busy) "保存中" else "保存")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss, enabled = !busy) {
        Text("キャンセル")
      }
    },
  )
}

private const val WEB_LIBRARY_DIAGNOSTIC_VALUE_MAX_LENGTH = 160
