package dev.terashima.yomitorirss.feature.rss

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun FeedScreen(
  modifier: Modifier,
  feeds: List<Feed>,
  onAdd: () -> Unit,
  onDelete: (Feed) -> Unit,
) {
  LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
    if (feeds.isEmpty()) {
      item {
        Column(
          Modifier.fillParentMaxSize(),
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text("フィードが登録されていません")
          Spacer(Modifier.height(16.dp))
          FilledTonalButton(onClick = onAdd) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("フィードを追加")
          }
        }
      }
    }
    items(feeds, key = Feed::id) { feed ->
      Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text(feed.title, style = MaterialTheme.typography.titleMedium)
            Text(
              feed.feedUrl,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            feed.lastFetchedAt?.let {
              Text("最終更新 ${feedTimeLabel(it)}", style = MaterialTheme.typography.labelSmall)
            }
            feed.lastError?.let {
              Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
          }
          IconButton(onClick = { onDelete(feed) }) {
            Icon(Icons.Default.Delete, "削除")
          }
        }
      }
    }
  }
}

private fun feedTimeLabel(value: String): String = runCatching {
  Instant.parse(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M/d HH:mm"))
}.getOrDefault("")
