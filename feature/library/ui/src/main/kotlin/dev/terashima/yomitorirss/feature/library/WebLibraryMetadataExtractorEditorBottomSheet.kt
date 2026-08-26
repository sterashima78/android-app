package dev.terashima.yomitorirss.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WebLibraryMetadataExtractorEditorBottomSheet(
  extractor: WebLibraryMetadataExtractor?,
  busy: Boolean,
  onDismiss: () -> Unit,
  onSave: (String?, String, String, Int) -> Unit,
  onTest: suspend (String, String, String, Int) -> WebLibraryMetadataExtractorTestResult,
  onError: (Throwable) -> Unit,
) {
  var urlPattern by remember(extractor?.id) { mutableStateOf(extractor?.urlPattern.orEmpty()) }
  var functionCode by remember(extractor?.id) { mutableStateOf(extractor?.functionCode.orEmpty()) }
  var timeoutText by remember(extractor?.id) {
    mutableStateOf((extractor?.timeoutSeconds ?: DEFAULT_WEB_LIBRARY_METADATA_TIMEOUT_SECONDS).toString())
  }
  var testUrl by remember(extractor?.id) { mutableStateOf("") }
  var testRunning by remember(extractor?.id) { mutableStateOf(false) }
  var testResult by remember(extractor?.id) { mutableStateOf<WebLibraryMetadataExtractorTestResult?>(null) }
  var testError by remember(extractor?.id) { mutableStateOf<String?>(null) }
  val scope = rememberCoroutineScope()
  val timeoutSeconds = timeoutText.toIntOrNull()
  val timeoutValid = timeoutSeconds != null &&
    timeoutSeconds in MIN_WEB_LIBRARY_METADATA_TIMEOUT_SECONDS..MAX_WEB_LIBRARY_METADATA_TIMEOUT_SECONDS
  val draftValid = urlPattern.isNotBlank() && functionCode.isNotBlank() && timeoutValid

  ModalBottomSheet(
    onDismissRequest = { if (!busy && !testRunning) onDismiss() },
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .fillMaxHeight(0.92f)
        .navigationBarsPadding()
        .imePadding()
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          if (extractor == null) "取得ルールを追加" else "取得ルールを編集",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.SemiBold,
        )
        TextButton(
          onClick = onDismiss,
          enabled = !busy && !testRunning,
        ) {
          Text("閉じる")
        }
      }

      Column(
        modifier = Modifier
          .weight(1f)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text("URL パターンでは * を任意長、? を1文字のワイルドカードとして利用できます。HTTPS のみ対象です。")
        OutlinedTextField(
          value = urlPattern,
          onValueChange = {
            urlPattern = it
            testResult = null
            testError = null
          },
          modifier = Modifier.fillMaxWidth(),
          label = { Text("URL パターン") },
          placeholder = { Text("https://example.com/books/*") },
          singleLine = true,
        )
        OutlinedTextField(
          value = timeoutText,
          onValueChange = {
            timeoutText = it.filter(Char::isDigit)
            testResult = null
            testError = null
          },
          modifier = Modifier.fillMaxWidth(),
          label = { Text("WebView タイムアウト（秒）") },
          supportingText = {
            Text(
              "$MIN_WEB_LIBRARY_METADATA_TIMEOUT_SECONDS〜$MAX_WEB_LIBRARY_METADATA_TIMEOUT_SECONDS 秒。" +
                "ページ読み込みから metadata 取得完了までの上限です。",
            )
          },
          isError = timeoutText.isNotBlank() && !timeoutValid,
          singleLine = true,
        )
        Text("関数は WebView 内で実行され、Promise<{ title, thumbnailUrl }> を返してください。async/await や fetch などの非同期処理も利用できます。値がない項目は null にできます。")
        OutlinedTextField(
          value = functionCode,
          onValueChange = {
            functionCode = it
            testResult = null
            testError = null
          },
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
        Text(
          "カスタムスクリプトはページ全体の読み込み完了を待たず、DOM が利用可能になった時点から開始します。" +
            "このコードは専用 WebView profile のページコンテキストで動作します。必要な非同期処理だけを行い、" +
            "不要な DOM 変更などの副作用は避けることを推奨します。",
        )

        HorizontalDivider()
        Text(
          "実行テスト",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          "保存前の入力内容をそのまま使い、指定 URL を専用 WebView で取得します。テスト URL と結果は保存しません。",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
          value = testUrl,
          onValueChange = {
            testUrl = it
            testResult = null
            testError = null
          },
          modifier = Modifier.fillMaxWidth(),
          label = { Text("テスト URL") },
          placeholder = { Text("https://example.com/books/1") },
          singleLine = true,
        )
        Button(
          onClick = {
            val timeout = timeoutSeconds ?: return@Button
            val normalizedTestUrl = testUrl.trim()
            scope.launch {
              testRunning = true
              testResult = null
              testError = null
              runCatching {
                onTest(normalizedTestUrl, urlPattern, functionCode, timeout)
              }.onSuccess {
                testResult = it
              }.onFailure { error ->
                testError = error.message?.takeIf(String::isNotBlank) ?: "取得テストに失敗しました"
                onError(error)
              }
              testRunning = false
            }
          },
          enabled = !busy && !testRunning && draftValid && testUrl.isNotBlank(),
        ) {
          Text(if (testRunning) "テスト中" else "実行テスト")
        }
        if (testRunning) {
          LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        testError?.let { message ->
          Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
        testResult?.let { result ->
          WebLibraryMetadataExtractorTestResultCard(result)
        }
        Spacer(Modifier.padding(bottom = 8.dp))
      }

      HorizontalDivider()
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TextButton(
          onClick = onDismiss,
          enabled = !busy && !testRunning,
        ) {
          Text("キャンセル")
        }
        TextButton(
          onClick = {
            val timeout = timeoutSeconds ?: return@TextButton
            onSave(extractor?.id, urlPattern, functionCode, timeout)
          },
          enabled = !busy && !testRunning && draftValid,
        ) {
          Text(if (busy) "保存中" else "保存")
        }
      }
    }
  }
}

@Composable
private fun WebLibraryMetadataExtractorTestResultCard(
  result: WebLibraryMetadataExtractorTestResult,
) {
  val execution = result.extractorExecution
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Text(
        "取得結果",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
      )
      Text(
        if (execution == null) {
          "取得ルールはこの URL に一致しませんでした。標準 metadata 取得結果を表示しています。"
        } else {
          "取得ルール: ${webLibraryMetadataExtractorTestStatusLabel(execution.status)}"
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      execution?.message?.takeIf(String::isNotBlank)?.let { message ->
        Text(
          message,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      execution?.let {
        Text(
          "カスタムタイトル: ${it.extractedTitle?.takeIf(String::isNotBlank) ?: "なし"}",
          style = MaterialTheme.typography.bodySmall,
        )
        Text(
          "カスタムサムネイル: ${it.extractedThumbnailUrl?.takeIf(String::isNotBlank) ?: "なし"}",
          style = MaterialTheme.typography.bodySmall,
        )
      }
      HorizontalDivider()
      Text("最終タイトル: ${result.book.title}", style = MaterialTheme.typography.bodySmall)
      Text(
        "最終サムネイル: ${result.book.thumbnailUrl?.takeIf(String::isNotBlank) ?: "なし"}",
        style = MaterialTheme.typography.bodySmall,
      )
      WebLibraryThumbnailPreview(book = result.book)
    }
  }
}

internal fun webLibraryMetadataExtractorTestStatusLabel(
  status: WebLibraryMetadataExtractorStatus,
): String = when (status) {
  WebLibraryMetadataExtractorStatus.MATCHED -> "ルール一致・開始待ち"
  WebLibraryMetadataExtractorStatus.RUNNING -> "実行中"
  WebLibraryMetadataExtractorStatus.APPLIED -> "適用成功"
  WebLibraryMetadataExtractorStatus.EMPTY_RESULT -> "空の結果"
  WebLibraryMetadataExtractorStatus.INVALID_FUNCTION -> "関数形式が不正"
  WebLibraryMetadataExtractorStatus.NON_PROMISE_RESULT -> "Promise を返していない"
  WebLibraryMetadataExtractorStatus.REJECTED -> "Promise が reject"
  WebLibraryMetadataExtractorStatus.THREW -> "実行時エラー"
  WebLibraryMetadataExtractorStatus.TIMED_OUT -> "タイムアウト"
  WebLibraryMetadataExtractorStatus.INVALID_STATE -> "実行状態が不正"
  WebLibraryMetadataExtractorStatus.INVALID_RESULT -> "戻り値が不正"
}
