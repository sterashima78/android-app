package dev.terashima.yomitorirss.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ChatGptDebugDialog(
  state: AiSettingsUiState,
  onDismiss: () -> Unit,
  onStartLogin: () -> Unit,
  onPollLogin: () -> Unit,
  onLogout: () -> Unit,
  onRefreshModels: () -> Unit,
  onSelectModel: (String) -> Unit,
  onPromptChange: (String) -> Unit,
  onRunInference: () -> Unit,
) {
  val uriHandler = LocalUriHandler.current
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("ChatGPT / Codex") },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        if (state.chatGptBusy || state.chatGptModelsLoading) {
          LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Text(
          if (state.chatGptConnected) {
            "接続済み${state.chatGptAccountLabel?.let { " ($it)" }.orEmpty()}"
          } else {
            "未接続"
          },
          style = MaterialTheme.typography.titleSmall,
        )
        state.chatGptExpiresAtEpochMillis?.let { Text("アクセストークン期限: ${formatEpochMillis(it)}") }
        state.chatGptStatusMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        state.chatGptError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (!state.chatGptConnected) {
          Button(onClick = onStartLogin, enabled = !state.chatGptBusy) { Text("ChatGPTでログイン") }
          state.chatGptLogin?.let { login ->
            HorizontalDivider()
            Text("1. ブラウザで次のページを開き、ChatGPTへログインします。")
            OutlinedButton(onClick = { uriHandler.openUri(login.verificationUrl) }) { Text("認証ページを開く") }
            Text("2. 次のコードを入力します。")
            SelectionContainer { Text(login.userCode, style = MaterialTheme.typography.headlineSmall) }
            Text("コード期限: ${formatEpochMillis(login.expiresAtEpochMillis)}")
            Text(
              "Device code認証が無効なアカウントやworkspaceでは、ChatGPT側で有効化が必要です。",
              style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = onPollLogin, enabled = !state.chatGptBusy) { Text("認証完了を確認") }
          }
        } else {
          OutlinedButton(onClick = onLogout, enabled = !state.chatGptBusy) { Text("ログアウト") }

          HorizontalDivider()
          Text("クラウドモデル", style = MaterialTheme.typography.titleSmall)
          Text(
            "ChatGPTアカウントで利用可能なモデル一覧から、Web検索対応モデルを選択します。タスクごとの実行先はAI実行設定で指定します。",
            style = MaterialTheme.typography.bodySmall,
          )
          OutlinedButton(
            onClick = onRefreshModels,
            enabled = !state.chatGptBusy && !state.chatGptModelsLoading,
          ) { Text("モデル一覧を更新") }
          if (state.chatGptModels.isEmpty() && !state.chatGptModelsLoading) {
            Text("利用可能なモデル一覧を取得してください。", style = MaterialTheme.typography.bodySmall)
          }
          state.chatGptModels.forEach { model ->
            ListItem(
              modifier = Modifier.clickable(enabled = !state.chatGptBusy) { onSelectModel(model.id) },
              headlineContent = { Text(model.name) },
              supportingContent = {
                Column {
                  Text(model.id)
                  model.description?.takeIf(String::isNotBlank)?.let { Text(it) }
                  Text("Web検索対応", style = MaterialTheme.typography.labelSmall)
                }
              },
              leadingContent = {
                RadioButton(
                  selected = state.chatGptSelectedModelId == model.id,
                  onClick = null,
                )
              },
            )
          }
          state.chatGptSelectedModelId?.takeIf { selected -> state.chatGptModels.none { it.id == selected } }?.let {
            Text(
              "前回選択したモデル $it は現在の利用可能モデル一覧にありません。別のモデルを選択してください。",
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.bodySmall,
            )
          }

          HorizontalDivider()
          Text("接続テスト", style = MaterialTheme.typography.titleSmall)
          Text(
            state.chatGptSelectedModelId?.let { "選択モデル: $it" } ?: "先にクラウドモデルを選択してください。",
            style = MaterialTheme.typography.bodySmall,
          )
          OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.chatGptPrompt,
            onValueChange = onPromptChange,
            label = { Text("テストプロンプト") },
            minLines = 3,
            enabled = !state.chatGptBusy,
          )
          Button(
            onClick = onRunInference,
            enabled = !state.chatGptBusy &&
              state.chatGptSelectedModelId != null &&
              state.chatGptPrompt.isNotBlank(),
          ) { Text("テスト推論") }
          state.chatGptResponse?.let { response ->
            HorizontalDivider()
            Text(
              "応答${state.chatGptElapsedMillis?.let { " (${it}ms)" }.orEmpty()}",
              style = MaterialTheme.typography.titleSmall,
            )
            SelectionContainer { Text(response) }
          }
        }
      }
    },
    confirmButton = {
      Row(modifier = Modifier.padding(start = 8.dp)) {
        TextButton(onClick = onDismiss) { Text("閉じる") }
      }
    },
  )
}

private fun formatEpochMillis(value: Long): String =
  DATE_TIME_FORMATTER.format(Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()))

private val DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
