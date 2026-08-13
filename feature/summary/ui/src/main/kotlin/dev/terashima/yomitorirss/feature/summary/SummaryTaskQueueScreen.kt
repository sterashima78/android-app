@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.terashima.yomitorirss.feature.summary

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
import dev.terashima.yomitorirss.feature.summary.SummaryQueueTask
import dev.terashima.yomitorirss.feature.summary.SummaryQueueTaskState
import dev.terashima.yomitorirss.feature.summary.SummaryTaskQueueRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SummaryTaskQueueScreen(
  repository: SummaryTaskQueueRepository,
  onDismiss: () -> Unit,
) {
  val taskQueueViewModel: SummaryTaskQueueViewModel = viewModel(
    factory = SummaryTaskQueueViewModel.Factory(repository),
  )
  val state by taskQueueViewModel.state.collectAsState()

  DisposableEffect(taskQueueViewModel) {
    taskQueueViewModel.startObserving()
    onDispose { taskQueueViewModel.stopObserving() }
  }

  SummaryTaskQueueContent(
    state = state,
    onDismiss = onDismiss,
    onStop = taskQueueViewModel::stop,
    onCancel = taskQueueViewModel::cancel,
    onResume = taskQueueViewModel::resume,
  )
}

@Composable
private fun SummaryTaskQueueContent(
  state: SummaryTaskQueueUiState,
  onDismiss: () -> Unit,
  onStop: (String) -> Unit,
  onCancel: (String) -> Unit,
  onResume: (String) -> Unit,
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
          title = { Text("タスクキュー") },
        )

        val runningCount = state.tasks.count { it.state == SummaryQueueTaskState.RUNNING }
        val queuedCount = state.tasks.count { it.state == SummaryQueueTaskState.QUEUED }
        val stoppedCount = state.tasks.count { it.state == SummaryQueueTaskState.STOPPED }
        Text(
          text = "実行中 ${runningCount}件 ・ 待機中 ${queuedCount}件 ・ 停止中 ${stoppedCount}件",
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
            text = "要約タスクはありません",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(24.dp),
          )
          else -> LazyColumn(Modifier.fillMaxSize()) {
            items(state.tasks, key = SummaryQueueTask::articleId) { item ->
              TaskRow(
                item = item,
                onStop = { onStop(item.articleId) },
                onCancel = { onCancel(item.articleId) },
                onResume = { onResume(item.articleId) },
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
private fun TaskRow(
  item: SummaryQueueTask,
  onStop: () -> Unit,
  onCancel: () -> Unit,
  onResume: () -> Unit,
) {
  Column(Modifier.fillMaxWidth()) {
    ListItem(
      headlineContent = { Text(item.articleTitle) },
      supportingContent = {
        Column {
          Text("${item.sourceTitle} ・ ${statusLabel(item.state)}")
          statusTime(item)?.let { Text(it) }
          if (item.state == SummaryQueueTaskState.RUNNING) {
            item.progressStage?.let {
              Text(
                text = progressLabel(item),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
              )
              val fraction = progressFraction(item)
              if (fraction == null) {
                LinearProgressIndicator(
                  modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
              } else {
                LinearProgressIndicator(
                  progress = { fraction },
                  modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
              }
            }
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

    when (item.state) {
      SummaryQueueTaskState.QUEUED,
      SummaryQueueTaskState.RUNNING -> Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
      ) {
        TextButton(onClick = onStop) { Text("停止") }
        TextButton(onClick = onCancel) { Text("キャンセル") }
      }

      SummaryQueueTaskState.STOPPED -> Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
      ) {
        TextButton(onClick = onResume) { Text("再開") }
        TextButton(onClick = onCancel) { Text("キャンセル") }
      }

      SummaryQueueTaskState.FAILED -> Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
      ) {
        TextButton(onClick = onResume) { Text("再実行") }
      }

      SummaryQueueTaskState.COMPLETED,
      SummaryQueueTaskState.CANCELLED,
      SummaryQueueTaskState.UNKNOWN -> Unit
    }
  }
}

private fun statusLabel(state: SummaryQueueTaskState): String = when (state) {
  SummaryQueueTaskState.QUEUED -> "待機中"
  SummaryQueueTaskState.RUNNING -> "実行中"
  SummaryQueueTaskState.COMPLETED -> "完了"
  SummaryQueueTaskState.FAILED -> "失敗"
  SummaryQueueTaskState.STOPPED -> "停止中"
  SummaryQueueTaskState.CANCELLED -> "キャンセル済み"
  SummaryQueueTaskState.UNKNOWN -> "不明"
}

internal fun progressLabel(item: SummaryQueueTask): String = when (item.progressStage) {
  SummaryQueueTaskProgressStage.FETCHING_ARTICLE -> "記事本文を取得しています"
  SummaryQueueTaskProgressStage.PREPARING_MODEL -> "AIモデルを読み込んでいます"
  SummaryQueueTaskProgressStage.GENERATING_SUMMARY -> "要約を生成しています"
  SummaryQueueTaskProgressStage.SUMMARIZING_CHUNK -> progressCountLabel("分割要約中", item)
  SummaryQueueTaskProgressStage.REDUCING_SUMMARY -> progressCountLabel("中間要約の統合中", item)
  SummaryQueueTaskProgressStage.FINALIZING_SUMMARY -> "最終要約を生成しています"
  SummaryQueueTaskProgressStage.UNKNOWN,
  null -> "処理しています"
}

internal fun progressFraction(item: SummaryQueueTask): Float? {
  val current = item.progressCurrent ?: return null
  val total = item.progressTotal ?: return null
  if (current <= 0 || total <= 0 || current > total) return null
  return ((current - 1).toFloat() / total.toFloat()).coerceIn(0f, 1f)
}

private fun progressCountLabel(prefix: String, item: SummaryQueueTask): String {
  val current = item.progressCurrent
  val total = item.progressTotal
  return if (current != null && total != null && current > 0 && total > 0) {
    "$prefix $current/$total"
  } else {
    prefix
  }
}

private fun statusTime(item: SummaryQueueTask): String? {
  val value = when (item.state) {
    SummaryQueueTaskState.RUNNING -> item.startedAt
    SummaryQueueTaskState.COMPLETED,
    SummaryQueueTaskState.FAILED,
    SummaryQueueTaskState.STOPPED,
    SummaryQueueTaskState.CANCELLED -> item.finishedAt
    SummaryQueueTaskState.QUEUED,
    SummaryQueueTaskState.UNKNOWN -> item.queuedAt
  } ?: return null
  val label = when (item.state) {
    SummaryQueueTaskState.RUNNING -> "開始"
    SummaryQueueTaskState.COMPLETED -> "完了"
    SummaryQueueTaskState.FAILED -> "失敗"
    SummaryQueueTaskState.STOPPED -> "停止"
    SummaryQueueTaskState.CANCELLED -> "キャンセル"
    SummaryQueueTaskState.QUEUED,
    SummaryQueueTaskState.UNKNOWN -> "追加"
  }
  return "$label ${formatTime(value)}"
}

private fun formatTime(value: String): String = runCatching {
  TIME_FORMAT.format(Instant.parse(value).atZone(ZoneId.systemDefault()))
}.getOrDefault(value)

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd HH:mm")
