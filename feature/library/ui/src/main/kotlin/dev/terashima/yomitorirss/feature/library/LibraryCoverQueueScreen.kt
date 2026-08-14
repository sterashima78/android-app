@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.terashima.yomitorirss.feature.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class CoverQueueFilter(val label: String) {
  QUEUE("取得待ち"), UNRESOLVED("未取得"), ACQUIRED("取得済み"), ALL("すべて"),
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
  var filter by remember { mutableStateOf(CoverQueueFilter.UNRESOLVED) }
  val visibleItems = snapshot.items.filter { item ->
    when (filter) {
      CoverQueueFilter.QUEUE -> item.state == LibraryCoverAcquisitionState.WAITING
      CoverQueueFilter.UNRESOLVED -> item.state == LibraryCoverAcquisitionState.NOT_FOUND || item.state == LibraryCoverAcquisitionState.AMBIGUOUS
      CoverQueueFilter.ACQUIRED -> item.state == LibraryCoverAcquisitionState.ACQUIRED || item.state == LibraryCoverAcquisitionState.SOURCE_PROVIDED
      CoverQueueFilter.ALL -> true
    }
  }
  val active = workStates.values.any { it == LibraryCoverWorkState.WAITING || it == LibraryCoverWorkState.RUNNING || it == LibraryCoverWorkState.RETRY_WAITING }
  val retryable = !active && snapshot.items.any { item ->
    (item.state == LibraryCoverAcquisitionState.WAITING || item.state == LibraryCoverAcquisitionState.NOT_FOUND || item.state == LibraryCoverAcquisitionState.AMBIGUOUS) &&
      workStates[item.source] != LibraryCoverWorkState.DISABLED
  }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    topBar = {
      TopAppBar(
        title = { Text("表紙取得状況") },
        navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Default.ArrowBack, "戻る") } },
      )
    },
  ) { padding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(padding),
      contentPadding = PaddingValues(bottom = 24.dp),
    ) {
      item { CoverSummary(snapshot) }
      items(listOf(LibrarySource.KINDLE, LibrarySource.AUDIBLE)) { source ->
        ListItem(
          headlineContent = { Text(source.label) },
          supportingContent = { Text(workStateLabel(workStates[source] ?: LibraryCoverWorkState.IDLE)) },
        )
      }
      item {
        Row(
          Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Button(onClick = onRetryUnresolved, enabled = retryable) { Text("未取得を再試行") }
          OutlinedButton(onClick = onCancelCurrentWork, enabled = active) { Text("実行をキャンセル") }
        }
      }
      message?.let { item { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } }
      item {
        Text(
          "未取得の行には解析用ID、取得経路、実エラーまたは推定原因を表示します。この画面のスクリーンショットを共有すれば、商品ページやAPIの現在の応答を再確認できます。",
          Modifier.padding(16.dp),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      item {
        Row(
          Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          CoverQueueFilter.entries.forEach { entry ->
            FilterChip(selected = filter == entry, onClick = { filter = entry }, label = { Text(entry.label) })
          }
        }
      }
      if (visibleItems.isEmpty()) {
        item { Text("対象の表紙はありません。", Modifier.padding(24.dp)) }
      } else {
        items(visibleItems, key = { "${it.source.name}:${it.sourceId}" }) { item ->
          ListItem(
            headlineContent = { Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            supportingContent = { Text(itemDetails(item)) },
          )
          HorizontalDivider()
        }
      }
    }
  }
}

@Composable
private fun CoverSummary(snapshot: LibraryCoverAcquisitionSnapshot) {
  Card(Modifier.fillMaxWidth().padding(16.dp)) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text("表紙", style = MaterialTheme.typography.titleMedium)
      Text("取得待ち ${snapshot.count(LibraryCoverAcquisitionState.WAITING)} 件")
      Text("取得済み ${snapshot.count(LibraryCoverAcquisitionState.ACQUIRED)} 件 / 元データあり ${snapshot.count(LibraryCoverAcquisitionState.SOURCE_PROVIDED)} 件")
      Text("見つからない ${snapshot.count(LibraryCoverAcquisitionState.NOT_FOUND)} 件 / 候補不明 ${snapshot.count(LibraryCoverAcquisitionState.AMBIGUOUS)} 件")
    }
  }
}

private fun itemDetails(item: LibraryCoverAcquisitionItem): String = buildString {
  append("${item.source.label} · ${stateLabel(item.state)}")
  item.lastAttemptAtEpochMillis?.let { append(" · 最終試行 ${formatAttempt(it)}") }
  item.provider?.let { append("\n取得経路/詳細: ${providerLabel(it)}") }
  if (item.state == LibraryCoverAcquisitionState.NOT_FOUND || item.state == LibraryCoverAcquisitionState.AMBIGUOUS) {
    append("\nsourceId=${item.sourceId}")
    append("\n推定: ${failureHint(item)}")
    append("\n調査対象: ${lookupTarget(item)}")
  }
}

private fun failureHint(item: LibraryCoverAcquisitionItem): String = when {
  item.state == LibraryCoverAcquisitionState.AMBIGUOUS -> "候補が複数。タイトル・著者・巻数の照合条件を確認"
  item.provider == "OPEN_LIBRARY" -> "Amazon商品ページで取得できず、Open Libraryでも一致する表紙を特定できなかった可能性"
  item.provider?.startsWith("KINDLE_COVER_ENRICHMENT") == true -> "上記の実エラーを確認。通信・リダイレクト・アクセス確認・解析処理のいずれかで失敗"
  item.provider?.startsWith("AUDIBLE_COVER_ENRICHMENT") == true -> "上記の実エラーを確認。Audible商品ページまたはCatalog APIの通信・解析処理で失敗"
  item.provider == "AUDIBLE_CATALOG_API_SEARCH" -> "商品ページ/ASIN検索で取得できず、タイトル・著者検索でも厳密一致しなかった可能性"
  item.provider == "AUDIBLE_CATALOG_API_ASIN" -> "Catalog APIのASIN検索で画像が見つからなかった可能性"
  item.source == LibrarySource.AUDIBLE -> "Audible商品ページまたはCatalog APIで表紙を特定できなかった可能性"
  else -> "取得先で表紙を特定できなかった可能性"
}

private fun lookupTarget(item: LibraryCoverAcquisitionItem): String = when (item.source) {
  LibrarySource.KINDLE -> "Amazon /dp/${item.sourceId} と Open Library検索"
  LibrarySource.AUDIBLE -> "Audible /pd/${item.sourceId} と Catalog API"
  LibrarySource.GOOGLE_PLAY_BOOKS -> "Google Play Books"
}

private fun stateLabel(state: LibraryCoverAcquisitionState): String = when (state) {
  LibraryCoverAcquisitionState.SOURCE_PROVIDED -> "元データに表紙あり"
  LibraryCoverAcquisitionState.ACQUIRED -> "取得済み"
  LibraryCoverAcquisitionState.WAITING -> "取得待ち"
  LibraryCoverAcquisitionState.NOT_FOUND -> "見つからない"
  LibraryCoverAcquisitionState.AMBIGUOUS -> "候補を特定できない"
}

private fun workStateLabel(state: LibraryCoverWorkState): String = when (state) {
  LibraryCoverWorkState.DISABLED -> "停止中"
  LibraryCoverWorkState.IDLE -> "待機なし"
  LibraryCoverWorkState.WAITING -> "開始待ち"
  LibraryCoverWorkState.RUNNING -> "取得中"
  LibraryCoverWorkState.RETRY_WAITING -> "通信エラー後の再試行待ち"
  LibraryCoverWorkState.FAILED -> "直近の処理でエラー"
}

private fun providerLabel(provider: String): String {
  val base = provider.substringBefore(" · ")
  val detail = provider.substringAfter(" · ", missingDelimiterValue = "")
  val label = when (base) {
    "OPEN_LIBRARY" -> "Open Library"
    "AMAZON_PRODUCT_PAGE_OGP" -> "Amazon 商品ページ (OGP)"
    "AMAZON_PRODUCT_PAGE_IMAGE" -> "Amazon 商品ページ (商品画像)"
    "KINDLE_COVER_ENRICHMENT" -> "Kindle 表紙補完"
    "AUDIBLE_PRODUCT_PAGE" -> "Audible 商品ページ"
    "AUDIBLE_CATALOG_API_ASIN" -> "Audible Catalog API (ASIN)"
    "AUDIBLE_CATALOG_API_SEARCH" -> "Audible Catalog API (検索)"
    "AUDIBLE_COVER_ENRICHMENT" -> "Audible 表紙補完"
    else -> base
  }
  return if (detail.isBlank()) label else "$label · $detail"
}

private fun formatAttempt(epochMillis: Long): String = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
  .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
