package dev.terashima.yomitorirss.feature.library

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

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
)

internal val LocalWebLibrarySettingsUiBinding =
  staticCompositionLocalOf<WebLibrarySettingsUiBinding?> { null }

internal fun webLibraryRefreshItemStatusLabel(status: WebLibraryRefreshItemStatus): String = when (status) {
  WebLibraryRefreshItemStatus.PENDING -> "待機中"
  WebLibraryRefreshItemStatus.RUNNING -> "取得中"
  WebLibraryRefreshItemStatus.UPDATED -> "更新あり"
  WebLibraryRefreshItemStatus.UNCHANGED -> "変更なし"
  WebLibraryRefreshItemStatus.WARNING -> "要確認"
  WebLibraryRefreshItemStatus.FAILED -> "失敗"
}

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
          "取得ルール「${execution.urlPattern}」に一致。カスタムスクリプト開始前（DOM 利用可能待ち）に WebView 処理が終了",
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

private const val WEB_LIBRARY_DIAGNOSTIC_VALUE_MAX_LENGTH = 160
