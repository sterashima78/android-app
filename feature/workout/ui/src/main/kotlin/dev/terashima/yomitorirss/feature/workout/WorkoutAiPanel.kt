package dev.terashima.yomitorirss.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.terashima.yomitorirss.core.designsystem.ChatMessageBubble

@Composable
fun WorkoutAiChatScreen(
  viewModel: WorkoutAiViewModel,
  modifier: Modifier = Modifier,
) {
  val state by viewModel.state.collectAsState()
  if (!state.initialized) {
    Column(modifier.fillMaxSize().padding(24.dp)) { Text("ワークアウトチャットを読み込んでいます…") }
    return
  }

  Column(
    modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Text("ワークアウトチャット", style = MaterialTheme.typography.titleLarge)
    Text(
      "現在のトレーニングメニュー、直近14日間の履歴、今日の記録とメモを使って回答します。",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    OutlinedTextField(
      value = state.memo,
      onValueChange = viewModel::updateMemo,
      modifier = Modifier.fillMaxWidth(),
      label = { Text("今日のワークアウトメモ・所感") },
      minLines = 1,
      maxLines = 3,
    )

    LazyColumn(
      modifier = Modifier.fillMaxWidth().weight(1f),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      if (state.lastRequestType == null && state.response == null && !state.loading) {
        item {
          Text(
            "下の操作から、今日のメニュー提案または完了後レビューを依頼できます。",
            modifier = Modifier.padding(vertical = 24.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      state.lastRequestType?.let { requestType ->
        item(key = "request") {
          ChatMessageBubble(
            isUser = true,
            content = requestType.userMessage(),
          )
        }
      }
      state.response?.let { response ->
        item(key = "response") {
          ChatMessageBubble(isUser = false, content = response)
        }
      }
      if (state.loading) {
        item(key = "progress") {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(
              if (state.settings.provider == WorkoutAiProvider.LOCAL) "Local AIで生成中…" else "ChatGPTで生成中…",
              style = MaterialTheme.typography.bodySmall,
            )
          }
        }
      }
      state.errorMessage?.let { message ->
        item(key = "error") {
          Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
      }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(
        onClick = viewModel::requestMenuSuggestion,
        enabled = !state.loading,
        modifier = Modifier.weight(1f),
      ) { Text("メニュー提案") }
      OutlinedButton(
        onClick = viewModel::requestPostWorkoutReview,
        enabled = !state.loading,
        modifier = Modifier.weight(1f),
      ) { Text("完了後レビュー") }
    }
    if (state.response != null || state.errorMessage != null) {
      TextButton(onClick = viewModel::clearResponse, modifier = Modifier.fillMaxWidth()) { Text("会話をクリア") }
    }
  }
}

@Composable
fun WorkoutAiSettingsSection(
  viewModel: WorkoutAiViewModel,
  modifier: Modifier = Modifier,
) {
  val state by viewModel.state.collectAsState()
  if (!state.initialized) return

  Card(modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text("ワークアウトチャット", style = MaterialTheme.typography.titleLarge)
      Text("実行先", style = MaterialTheme.typography.labelLarge)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
          selected = state.settings.provider == WorkoutAiProvider.LOCAL,
          onClick = { viewModel.setProvider(WorkoutAiProvider.LOCAL) },
          label = { Text("Local") },
        )
        FilterChip(
          selected = state.settings.provider == WorkoutAiProvider.CHATGPT,
          onClick = { viewModel.setProvider(WorkoutAiProvider.CHATGPT) },
          label = { Text("ChatGPT") },
        )
      }
      if (state.settings.provider == WorkoutAiProvider.CHATGPT) {
        Text(
          "直近14日間のワークアウト記録、メモ、方針、設定済みトレーニングメニューがクラウドへ送信されます。自動でLocalへ切り替えません。",
          style = MaterialTheme.typography.bodySmall,
        )
      }
      OutlinedTextField(
        value = state.settings.workoutPolicy,
        onValueChange = viewModel::updateWorkoutPolicy,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("ワークアウト方針") },
        placeholder = { Text("例: 継続を優先し、前回より少しだけ負荷を上げる") },
        minLines = 2,
        maxLines = 4,
      )
      Text(
        "候補種目はこの設定画面の「登録済み種目」をそのままチャットへ渡すため、別途入力する必要はありません。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

private fun WorkoutAiRequestType.userMessage(): String = when (this) {
  WorkoutAiRequestType.MENU_SUGGESTION -> "今日のトレーニングメニューを提案して"
  WorkoutAiRequestType.POST_WORKOUT_REVIEW -> "今日のトレーニングをレビューして"
}
