@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.terashima.yomitorirss.feature.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class CoverQueueFilter(val label: String) {
  QUEUE("取得待ち"),
  UNRESOLVED("未取得"),
  ACQUIRED("取得済み"),
  ALL("すべて"),
}

@Composable
fun LibraryCoverQueueScreen(
  snapshot: LibraryCoverAcquisitionSnapshot,
  workStates: Map<LibrarySource, LibraryCoverWorkState>,
  message: String?,
  onRetryUnresolved: () -> Unit,
  onCancelCurrentWork: () -> Unit,
  onDismiss: () -> Unit,
) {
  var selectedFilter by remember { mutableStateOf(CoverQueueFilter.QUEUE) }
  val visibleItems = remember(snapshot.items, selectedFilter) {
    snapshot.items.filter { item ->
      when (selectedFilter) {
        CoverQueueFilter.QUEUE -> item.state == LibraryCoverAcquisitionState.WAITING
        CoverQueueFilter.UNRESOLVED -> item.state == LibraryCoverAcquisitionState.NOT_FOUND ||
          item.state == LibraryCoverAcquisitionState.AMBIGUOUS
        CoverQueueFilter.ACQUIRED -> item.state == LibraryCoverAcquisitionState.ACQUIRED ||
          item.state == LibraryCoverAcquisitionState.SOURCE_PROVIDED
        CoverQueueFilter.ALL -> true
      }
    }
  }
  val active = workStates.values.any { state ->
    state == LibraryCoverWorkState.WAITING ||
      state == LibraryCoverWorkState.RUNNING ||
      state == LibraryCoverWorkState.RETRY_WAITING
  }
  val retryable = !active && snapshot.items.any { item ->
    val unresolved = item.state == LibraryCoverAcquisitionState.WAITING ||
      item.state == LibraryCoverAcquisitionState.NOT_FOUND ||
      item.state == LibraryCoverAcquisitionState.AMBIGUOUS
    unresolved && workStates[item.source] != LibraryCoverWorkState.DISABLED
  }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    topBar = {
      TopAppBar(
        title = { Text("表紙取得状況") },
        navigationIcon = {
          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
          }
        },
      )
    },
  ) { padding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding),
      contentPadding = PaddingValues(bottom = 24.dp),
    ) {
      item { CoverQueueSummary(snapshot) }
      item {
        Text(
          "バックグラウンド処理",
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
          style = MaterialTheme.typography.titleMedium,
        )
      }
      items(listOf(LibrarySource.KINDLE, LibrarySource.AUDIBLE)) { source ->
        ListItem(
          headlineContent = { Text(source.label) },
          supportingContent = {
            Text(coverWorkStateLabel(workStates[source] ?: LibraryCoverWorkState.IDLE))
          },
        )
      }
      item {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Button(
            onClick = onRetryUnresolved,
            enabled = retryable,
          ) { Text("未取得を再試行") }
          OutlinedButton(
            onClick = onCancelCurrentWork,
            enabled = active,
          ) { Text("実行をキャンセル") }
        }
      }
      message?.let { text ->
        item {
          Text(
            text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      item {
        Text(
          "書籍ごとの待機列は専用テーブルへコピーせず、蔵書DBと表紙メタデータから再構成します。実行ジョブは WorkManager が端末内に永続化します。キャンセルは現在の実行だけを止め、次回の通常トリガーでは再び開始されます。",
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      item { HorizontalDivider() }
      item {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          CoverQueueFilter.entries.forEach { filter ->
            FilterChip(
              selected = selectedFilter == filter,
              onClick = { selectedFilter = filter },
              label = { Text(filter.label) },
            )
          }
        }
      }
      if (visibleItems.isEmpty()) {
        item {
          Text(
            when (selectedFilter) {
              CoverQueueFilter.QUEUE -> "取得待ちの表紙はありません。"
              CoverQueueFilter.UNRESOLVED -> "未取得の表紙はありません。"
              CoverQueueFilter.ACQUIRED -> "取得済みの表紙はありません。"
              CoverQueueFilter.ALL -> "対象の Kindle / Audible 蔵書はありません。"
            },
            modifier = Modifier.padding(24.dp),
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      } else {
        items(
          items = visibleItems,
          key = { item -> "${item.source.name}:${item.sourceId}" },
        ) { item ->
          CoverQueueItem(item)
          HorizontalDivider()
        }
      }
    }
  }
}

@Composable
private fun CoverQueueSummary(snapshot: LibraryCoverAcquisitionSnapshot) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .padding(16.dp),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Text("表紙", style = MaterialTheme.typography.titleMedium)
      Text("取得待ち ${snapshot.count(LibraryCoverAcquisitionState.WAITING)} 件")
      Text(
        "取得済み ${snapshot.count(LibraryCoverAcquisitionState.ACQUIRED)} 件 / " +
          "元データに表紙あり ${snapshot.count(LibraryCoverAcquisitionState.SOURCE_PROVIDED)} 件",
      )
      Text(
        "見つからない ${snapshot.count(LibraryCoverAcquisitionState.NOT_FOUND)} 件 / " +
          "候補不明 ${snapshot.count(LibraryCoverAcquisitionState.AMBIGUOUS)} 件",
      )
    }
  }
}

@Composable
private fun CoverQueueItem(item: LibraryCoverAcquisitionItem) {
  val details = buildList {
    add(item.source.label)
    add(coverAcquisitionStateLabel(item.state))
    item.provider?.let { add(coverProviderLabel(it)) }
    item.lastAttemptAtEpochMillis?.let { add(formatCoverAttemptTime(it)) }
  }.joinToString(" · ")

  ListItem(
    headlineContent = {
      Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
    },
    supportingContent = { Text(details) },
  )
}

private fun coverAcquisitionStateLabel(state: LibraryCoverAcquisitionState): String = when (state) {
  LibraryCoverAcquisitionState.SOURCE_PROVIDED -> "元データに表紙あり"
  LibraryCoverAcquisitionState.ACQUIRED -> "取得済み"
  LibraryCoverAcquisitionState.WAITING -> "取得待ち"
  LibraryCoverAcquisitionState.NOT_FOUND -> "見つからない"
  LibraryCoverAcquisitionState.AMBIGUOUS -> "候補を特定できない"
}

private fun coverWorkStateLabel(state: LibraryCoverWorkState): String = when (state) {
  LibraryCoverWorkState.DISABLED -> "停止中"
  LibraryCoverWorkState.IDLE -> "待機なし"
  LibraryCoverWorkState.WAITING -> "開始待ち"
  LibraryCoverWorkState.RUNNING -> "取得中"
  LibraryCoverWorkState.RETRY_WAITING -> "通信エラー後の再試行待ち"
  LibraryCoverWorkState.FAILED -> "直近の処理でエラー"
}

private fun coverProviderLabel(provider: String): String = when (provider) {
  "OPEN_LIBRARY" -> "Open Library"
  "AMAZON_PRODUCT_PAGE_OGP" -> "Amazon 商品ページ (OGP)"
  "AMAZON_PRODUCT_PAGE_IMAGE" -> "Amazon 商品ページ (商品画像)"
  "AUDIBLE_PRODUCT_PAGE" -> "Audible 商品ページ"
  "AUDIBLE_CATALOG_API_ASIN" -> "Audible Catalog API (ASIN)"
  "AUDIBLE_CATALOG_API_SEARCH" -> "Audible Catalog API (検索)"
  else -> provider
}

private fun formatCoverAttemptTime(epochMillis: Long): String {
  val formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
  val local = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
  return "最終試行 ${formatter.format(local)}"
}