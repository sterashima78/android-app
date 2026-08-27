package dev.terashima.yomitorirss.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun WorkoutAiPanel(
  viewModel: WorkoutAiViewModel,
  modifier: Modifier = Modifier,
) {
  val state by viewModel.state.collectAsState()
  if (!state.initialized) return

  Card(
    modifier
      .fillMaxWidth()
      .heightIn(max = 420.dp)
      .padding(horizontal = 12.dp, vertical = 8.dp),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text("AIワークアウト", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        TextButton(onClick = viewModel::toggleSettings) {
          Text(if (state.settingsExpanded) "設定を閉じる" else "AI設定")
        }
      }

      OutlinedTextField(
        value = state.memo,
        onValueChange = viewModel::updateMemo,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("今日のワークアウトメモ・所感") },
        minLines = 1,
        maxLines = 3,
      )

      if (state.settingsExpanded) {
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
            "直近14日間のワークアウト記録、メモ、方針、メニュー候補がクラウドへ送信されます。自動でLocalへ切り替えません。",
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
        OutlinedTextField(
          value = state.settings.menuCandidates,
          onValueChange = viewModel::updateMenuCandidates,
          modifier = Modifier.fillMaxWidth(),
          label = { Text("メニュー候補") },
          placeholder = { Text("例: 腕立て伏せ、ランジ、プランク") },
          minLines = 2,
          maxLines = 4,
        )
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

      if (state.loading) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(
          if (state.settings.provider == WorkoutAiProvider.LOCAL) "Local AIで生成中…" else "ChatGPTで生成中…",
          style = MaterialTheme.typography.bodySmall,
        )
      }

      state.response?.let { response ->
        Text("AIの回答", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text(response, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = viewModel::clearResponse) { Text("回答を閉じる") }
      }
      state.errorMessage?.let { message ->
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
      }
    }
  }
}
