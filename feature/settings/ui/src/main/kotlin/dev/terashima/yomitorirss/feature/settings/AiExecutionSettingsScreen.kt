@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.terashima.yomitorirss.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.terashima.yomitorirss.feature.summary.SummaryExecutionProvider

@Composable
fun AiExecutionSettingsScreen(
  state: AiSettingsUiState,
  onDismiss: () -> Unit,
  onSummaryProviderChange: (SummaryExecutionProvider) -> Unit,
) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(modifier = Modifier.fillMaxSize()) {
      Column(Modifier.fillMaxSize()) {
        TopAppBar(
          navigationIcon = {
            IconButton(onClick = onDismiss) {
              Icon(Icons.Default.ArrowBack, contentDescription = "設定へ戻る")
            }
          },
          title = { Text("AI実行設定") },
        )

        Text(
          text = "AIタスクごとに実行先を選択します。プロバイダのログインや利用モデルは各プロバイダ設定で管理します。",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        HorizontalDivider()

        Text(
          text = "記事要約・タグ付け",
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        ProviderChoice(
          title = "ローカル",
          supporting = "端末内の選択済みモデルで記事本文を取得し、要約・タグ付けを実行",
          selected = state.summaryExecutionProvider == SummaryExecutionProvider.LOCAL,
          enabled = true,
          onClick = { onSummaryProviderChange(SummaryExecutionProvider.LOCAL) },
        )
        val cloudAvailable = state.chatGptConnected && state.chatGptSelectedModelId != null
        ProviderChoice(
          title = "ChatGPT / Codex",
          supporting = if (cloudAvailable) {
            "${state.chatGptSelectedModelId} を使い、記事URLをWeb検索で開いてクラウドで要約・タグ付けを実行"
          } else {
            "ChatGPT / Codex設定でログインし、Web検索対応モデルを選択してください"
          },
          selected = state.summaryExecutionProvider == SummaryExecutionProvider.CHATGPT,
          enabled = cloudAvailable,
          onClick = { onSummaryProviderChange(SummaryExecutionProvider.CHATGPT) },
        )
        Text(
          text = "クラウド実行では記事URL、要約指示、生成済み要約、既存タグ・フォルダ候補など処理に必要な情報をChatGPTへ送信します。",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        state.chatGptError?.let { error ->
          Text(
            text = error,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun ProviderChoice(
  title: String,
  supporting: String,
  selected: Boolean,
  enabled: Boolean,
  onClick: () -> Unit,
) {
  ListItem(
    modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
    headlineContent = { Text(title) },
    supportingContent = { Text(supporting) },
    leadingContent = { RadioButton(selected = selected, onClick = null, enabled = enabled) },
  )
}
