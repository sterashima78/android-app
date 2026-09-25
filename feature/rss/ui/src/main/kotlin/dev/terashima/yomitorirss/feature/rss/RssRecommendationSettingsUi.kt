package dev.terashima.yomitorirss.feature.rss

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun RssRecommendationSettingsUi(
  state: RssUiState,
  onSaveManualCondition: (String) -> Unit,
  onResetLearning: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var editing by remember { mutableStateOf(false) }

  Card(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 12.dp),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text("推薦フィルタ", style = MaterialTheme.typography.titleMedium)
      Text(
        "記事タイトルだけを使い、除外条件に基づいて評価できる記事を1〜10でスコアリングします。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Text("手動の除外条件", style = MaterialTheme.typography.labelLarge)
      Text(
        state.recommendationPolicy.manualCondition.ifBlank { "未設定" },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 4,
        overflow = TextOverflow.Ellipsis,
      )
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = { editing = true }) {
          Text("編集")
        }
      }

      Text("学習した除外条件", style = MaterialTheme.typography.labelLarge)
      Text(
        state.recommendationPolicy.learnedCondition.ifBlank { "まだ学習条件はありません" },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 4,
        overflow = TextOverflow.Ellipsis,
      )
      if (state.recommendationPendingFeedbackCount > 0 || state.recommendationLearning) {
        Text(
          if (state.recommendationLearning) {
            "除外参考から学習中…"
          } else {
            "学習待ち: ${state.recommendationPendingFeedbackCount}件"
          },
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.secondary,
        )
      }
      if (state.recommendationPolicy.learnedCondition.isNotBlank()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
          TextButton(onClick = onResetLearning) {
            Text("学習条件をリセット")
          }
        }
      }
    }
  }

  if (editing) {
    RssRecommendationConditionDialog(
      initialValue = state.recommendationPolicy.manualCondition,
      onDismiss = { editing = false },
      onSave = {
        editing = false
        onSaveManualCondition(it)
      },
    )
  }
}

@Composable
private fun RssRecommendationConditionDialog(
  initialValue: String,
  onDismiss: () -> Unit,
  onSave: (String) -> Unit,
) {
  var value by remember(initialValue) { mutableStateOf(initialValue) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("推薦の除外条件") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          "読みたい必要性が低い記事の特徴を自然文で指定します。条件に該当しない、または判断できない記事は除外しません。",
          style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
          value = value,
          onValueChange = { value = it },
          modifier = Modifier.fillMaxWidth(),
          minLines = 5,
          maxLines = 10,
          label = { Text("除外条件") },
        )
      }
    },
    confirmButton = {
      TextButton(onClick = { onSave(value) }) {
        Text("保存")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("キャンセル")
      }
    },
  )
}
