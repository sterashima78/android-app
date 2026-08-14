@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.terashima.yomitorirss.feature.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun LibraryCoverQueueScreen(
  snapshot: LibraryCoverAcquisitionSnapshot,
  workStates: Map<LibrarySource, LibraryCoverWorkState>,
  message: String?,
  onRetryUnresolved: () -> Unit,
  onCancelCurrentWork: () -> Unit,
  onDismiss: () -> Unit,
) {
  val active = workStates.values.any { it == LibraryCoverWorkState.WAITING || it == LibraryCoverWorkState.RUNNING || it == LibraryCoverWorkState.RETRY_WAITING }
  val retryable = !active && snapshot.items.any { item ->
    item.state == LibraryCoverAcquisitionState.WAITING || item.state == LibraryCoverAcquisitionState.NOT_FOUND || item.state == LibraryCoverAcquisitionState.AMBIGUOUS
  }
  Scaffold(
    modifier = Modifier.fillMaxSize(),
    topBar = {
      TopAppBar(
        title = { Text("表紙取得状況") },
        navigationIcon = {
          IconButton(onClick = onDismiss) { Icon(Icons.Default.ArrowBack, contentDescription = "戻る") }
        },
      )
    },
  ) { padding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(padding),
      contentPadding = PaddingValues(bottom = 24.dp),
    ) {
      item {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("取得待ち ${snapshot.count(LibraryCoverAcquisitionState.WAITING)} 件")
          Text("見つからない ${snapshot.count(LibraryCoverAcquisitionState.NOT_FOUND)} 件 / 候補不明 ${snapshot.count(LibraryCoverAcquisitionState.AMBIGUOUS)} 件")
          Text("Kindle: ${workStateLabel(workStates[LibrarySource.KINDLE])}")
          Text("Audible: ${workStateLabel(workStates[LibrarySource.AUDIBLE])}")
          Text("未取得項目では解析用に sourceId、取得経路、状態、最終試行時刻を表示します。画面を共有すれば取得方法の調査に利用できます。", style = MaterialTheme.typography.bodySmall)
        }
      }
      item {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(onClick = onRetryUnresolved, enabled = retryable) { Text("未取得を再試行") }
          OutlinedButton(onClick = onCancelCurrentWork, enabled = active) { Text("実行をキャンセル") }
        }
      }
      message?.let { item { Text(it, Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall) } }
      items(snapshot.items, key = { "${it.source.name}:${it.sourceId}" }) { item ->
        ListItem(
          headlineContent = { Text(item.title) },
          supportingContent = { Text(itemDetail(item)) },
        )
        HorizontalDivider()
      }
    }
  }
}

private fun itemDetail(item: LibraryCoverAcquisitionItem): String = buildString {
  append("${item.source.label} · ${item.state.name}")
  item.provider?.let { append(" · $it") }
  item.lastAttemptAtEpochMillis?.let { append(" · ${formatAttempt(it)}") }
  append("\nsourceId=${item.sourceId}")
}

private fun workStateLabel(state: LibraryCoverWorkState?): String = state?.name ?: LibraryCoverWorkState.IDLE.name

private fun formatAttempt(epochMillis: Long): String {
  val formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
  return formatter.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
}
