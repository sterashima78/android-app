@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.terashima.yomitorirss.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun AiTaskQueueScreen(
  repository: AiTaskQueueRepository,
  onDismiss: () -> Unit,
) {
  val taskQueueViewModel: AiTaskQueueViewModel = viewModel(
    factory = AiTaskQueueViewModel.Factory(repository),
  )
  val state by taskQueueViewModel.state.collectAsState()

  DisposableEffect(taskQueueViewModel) {
    taskQueueViewModel.startObserving()
    onDispose { taskQueueViewModel.stopObserving() }
  }

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
          title = { Text("AIタスクキュー") },
        )

        ListItem(
          headlineContent = { Text("AIタスクを一時停止") },
          supportingContent = {
            Text(
              if (state.queuePaused) {
                "新しいAIタスクも待機させ、バックグラウンドのローカルAI処理を開始しません"
              } else {
                "要約・タグ付け・蔵書整理などのAIタスクをバックグラウンドで順次実行します"
              },
            )
          },
          trailingContent = {
            Switch(
              checked = state.queuePaused,
              onCheckedChange = taskQueueViewModel::setPaused,
            )
          },
        )
        ListItem(
          headlineContent = { Text("充電時に自動再開") },
          supportingContent = {
            Text("一時停止中に端末が充電状態になると、AIタスクの自動実行を再開します")
          },
          trailingContent = {
            Switch(
              checked = state.resumeWhenCharging,
              onCheckedChange = taskQueueViewModel::setResumeWhenCharging,
            )
          },
        )
        HorizontalDivider()

        val runningCount = state.tasks.count { it.state == AiTaskQueueItemState.RUNNING }
        val queuedCount = state.tasks.count { it.state == AiTaskQueueItemState.QUEUED }
        val pausedCount = state.tasks.count {
          it.state == AiTaskQueueItemState.PAUSED || it.state == AiTaskQueueItemState.STOPPED
        }
        val executionLabel = if (state.queuePaused) "自動実行 一時停止中" else "自動実行中"
        Text(
          text = "$executionLabel ・ 実行中 ${runningCount}件 ・ 待機中 ${queuedCount}件 ・ 停止中 ${pausedCount}件",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        state.actionError?.let { message ->
          Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
          )
        }

        when {
          state.loading -> CircularProgressIndicator(Modifier.padding(24.dp))
          state.tasks.isEmpty() -> Text(
            text = "AIタスクはありません",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(24.dp),
          )
          else -> LazyColumn(Modifier.fillMaxSize()) {
            items(state.tasks, key = AiTaskQueueItem::id) { item ->
              AiTaskRow(
                item = item,
                onStop = { taskQueueViewModel.stop(item.id) },
                onCancel = { taskQueueViewModel.cancel(item.id) },
                onResume = { taskQueueViewModel.resume(item.id) },
              )
              HorizontalDivider()
            }
          }
        }
      }
    }
  }
}

@Composable
private fun AiTaskRow(
  item: AiTaskQueueItem,
  onStop: () -> Unit,
  onCancel: () -> Unit,
  onResume: () -> Unit,
) {
  Column(Modifier.fillMaxWidth()) {
    ListItem(
      headlineContent = { Text(item.title) },
      supportingContent = {
        Column {
          Text("${item.source} ・ ${statusLabel(item.state)}")
          val current = item.progressCurrent
          val total = item.progressTotal
          if (current != null && total != null && total > 0) {
            Text(
              text = "進捗 $current/$total",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(top = 4.dp),
            )
            LinearProgressIndicator(
              progress = { (current.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
              modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
          }
          item.pendingReviewCount?.takeIf { it > 0 }?.let { count ->
            Text(
              text = "確認待ち $count件",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(top = 4.dp),
            )
          }
          item.error?.takeIf(String::isNotBlank)?.let { error ->
            Text(
              text = error,
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.bodySmall,
            )
          }
        }
      },
    )

    if (item.canStop || item.canCancel || item.canResume) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
      ) {
        if (item.canStop) TextButton(onClick = onStop) { Text("停止") }
        if (item.canResume) {
          TextButton(onClick = onResume) {
            Text(if (item.state == AiTaskQueueItemState.FAILED) "再実行" else "再開")
          }
        }
        if (item.canCancel) TextButton(onClick = onCancel) { Text("キャンセル") }
      }
    }
  }
}

private fun statusLabel(state: AiTaskQueueItemState): String = when (state) {
  AiTaskQueueItemState.QUEUED -> "待機中"
  AiTaskQueueItemState.RUNNING -> "実行中"
  AiTaskQueueItemState.PAUSED -> "一時停止中"
  AiTaskQueueItemState.COMPLETED -> "完了"
  AiTaskQueueItemState.FAILED -> "失敗"
  AiTaskQueueItemState.STOPPED -> "停止中"
  AiTaskQueueItemState.CANCELLED -> "キャンセル済み"
  AiTaskQueueItemState.UNKNOWN -> "不明"
}
