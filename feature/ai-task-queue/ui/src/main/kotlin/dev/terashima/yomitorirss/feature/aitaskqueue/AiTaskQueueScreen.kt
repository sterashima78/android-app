@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.terashima.yomitorirss.feature.aitaskqueue

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
          headlineContent = { Text("ローカルAIを一時停止") },
          supportingContent = {
            Text(
              if (state.localPaused) {
                "端末内モデルを使うAIタスクを待機させます。クラウドAIタスクは継続できます"
              } else {
                "端末内モデルを使う要約・タグ付け・蔵書整理・書誌正規化・LLM Wiki生成を実行します"
              },
            )
          },
          trailingContent = {
            Switch(
              checked = state.localPaused,
              onCheckedChange = taskQueueViewModel::setLocalPaused,
            )
          },
        )
        ListItem(
          headlineContent = { Text("クラウドAIを一時停止") },
          supportingContent = {
            Text(
              if (state.cloudPaused) {
                "ChatGPT / Codexなどクラウドプロバイダを使うAIタスクを待機させます"
              } else {
                "クラウド実行に設定したAIタスクをローカルAIとは独立して実行します"
              },
            )
          },
          trailingContent = {
            Switch(
              checked = state.cloudPaused,
              onCheckedChange = taskQueueViewModel::setCloudPaused,
            )
          },
        )
        ListItem(
          headlineContent = { Text("充電時にローカルAIを自動再開") },
          supportingContent = {
            Text("ローカルAIの一時停止中に端末が充電状態になると、端末内AIタスクを自動再開します")
          },
          trailingContent = {
            Switch(
              checked = state.resumeLocalWhenCharging,
              onCheckedChange = taskQueueViewModel::setResumeLocalWhenCharging,
            )
          },
        )
        ListItem(
          headlineContent = { Text("失敗したブックマーク") },
          supportingContent = {
            Text("生成に失敗した保存済みブックマークをまとめて待機状態へ戻します")
          },
          trailingContent = {
            TextButton(onClick = taskQueueViewModel::retryFailedBookmarkTasks) {
              Text("一括再実行")
            }
          },
        )
        HorizontalDivider()

        val runningCount = state.taskCounts.running
        val queuedCount = state.taskCounts.queued
        val pausedCount = state.taskCounts.pausedOrStopped
        val localLabel = if (state.localPaused) "ローカル停止" else "ローカル実行"
        val cloudLabel = if (state.cloudPaused) "クラウド停止" else "クラウド実行"
        Text(
          text = "$localLabel ・ $cloudLabel ・ 実行中 ${runningCount}件 ・ 待機中 ${queuedCount}件 ・ 停止中 ${pausedCount}件",
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
      headlineContent = { Text(taskTitle(item)) },
      supportingContent = {
        Column {
          Text("${taskSource(item)} ・ ${statusLabel(item.state)} ・ ${priorityLabel(item.priority)}")
          aiTaskProgressPresentation(item)?.let { progress ->
            Text(
              text = progress.label,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(top = 4.dp),
            )
            if (progress.fraction == null) {
              LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
              )
            } else {
              LinearProgressIndicator(
                progress = { progress.fraction },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
              )
            }
          }
          item.pendingReviewCount?.takeIf { it > 0 }?.let { count ->
            Text(
              text = "確認待ち ${count}件",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(top = 4.dp),
            )
          }
          aiTaskFailurePresentation(item)?.let { failure ->
            Text(
              text = failure.label,
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.labelSmall,
              modifier = Modifier.padding(top = 6.dp),
            )
            Text(
              text = failure.reason,
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
        if (item.canStop) {
          TextButton(onClick = onStop) {
            Text(if (item.kind == AiTaskQueueItemKind.LIBRARY_ORGANIZATION) "一時停止" else "停止")
          }
        }
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

internal data class AiTaskProgressPresentation(
  val label: String,
  val fraction: Float?,
)

internal fun aiTaskProgressPresentation(item: AiTaskQueueItem): AiTaskProgressPresentation? {
  if (item.state != AiTaskQueueItemState.RUNNING) return null

  val current = item.progressCurrent
  val total = item.progressTotal
  val determinate = current != null && total != null && total > 0
  val numberedProgress = if (determinate) " $current/$total" else ""
  val label = when (item.progressStage) {
    AiTaskQueueProgressStage.FETCHING_CONTENT -> "記事本文を取得中"
    AiTaskQueueProgressStage.PREPARING_MODEL -> "AIモデルを読み込み中"
    AiTaskQueueProgressStage.GENERATING -> "要約を生成中"
    AiTaskQueueProgressStage.PROCESSING_CHUNK -> "長文を分割要約中$numberedProgress"
    AiTaskQueueProgressStage.REDUCING -> "中間要約を統合中$numberedProgress"
    AiTaskQueueProgressStage.FINALIZING -> "最終要約を生成中"
    AiTaskQueueProgressStage.CLOUD_GENERATING -> "クラウドで記事を要約中"
    AiTaskQueueProgressStage.CLOUD_ENRICHING -> "クラウドでタグ・フォルダ候補を生成中"
    AiTaskQueueProgressStage.UNKNOWN,
    null -> if (determinate) "進捗$current/$total" else "AI処理中"
  }
  val fraction = if (determinate) {
    (current!!.toFloat() / total!!.toFloat()).coerceIn(0f, 1f)
  } else {
    null
  }
  return AiTaskProgressPresentation(label = label, fraction = fraction)
}

internal data class AiTaskFailurePresentation(
  val label: String,
  val reason: String,
)

internal fun aiTaskFailurePresentation(item: AiTaskQueueItem): AiTaskFailurePresentation? {
  val error = item.error?.trim()?.takeIf(String::isNotEmpty)
  return when (item.state) {
    AiTaskQueueItemState.FAILED -> AiTaskFailurePresentation(
      label = "失敗理由",
      reason = error ?: "詳細な失敗理由は記録されていません",
    )
    AiTaskQueueItemState.QUEUED -> error?.let {
      AiTaskFailurePresentation(
        label = "直前の失敗・自動再試行待ち",
        reason = it,
      )
    }
    else -> null
  }
}

internal fun aiTaskFailureReason(item: AiTaskQueueItem): String? = aiTaskFailurePresentation(item)?.reason

private fun taskTitle(item: AiTaskQueueItem): String = item.title

private fun taskSource(item: AiTaskQueueItem): String {
  val source = when (item.kind) {
    AiTaskQueueItemKind.SUMMARY -> "要約 ・ ${item.source}"
    AiTaskQueueItemKind.LIBRARY_ORGANIZATION -> "蔵書整理 ・ ${item.source}"
    AiTaskQueueItemKind.SMB_METADATA_NORMALIZATION -> "書誌正規化 ・ ${item.source}"
    AiTaskQueueItemKind.KNOWLEDGE_WIKI -> "LLM Wiki ・ ${item.source}"
  }
  return item.executionProviderLabel?.takeIf(String::isNotBlank)?.let { "$source ・ $it" } ?: source
}

private fun priorityLabel(priority: AiTaskQueueItemPriority): String = when (priority) {
  AiTaskQueueItemPriority.HIGH -> "優先度 高"
  AiTaskQueueItemPriority.NORMAL -> "優先度 通常"
  AiTaskQueueItemPriority.LOW -> "優先度 低"
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
