package dev.terashima.yomitorirss.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
internal fun TrafficUsageDialog(reader: TrafficUsageReader, onDismiss: () -> Unit) {
  var days by remember { mutableIntStateOf(30) }
  var transport by remember { mutableStateOf("mobile") }
  var overview by remember { mutableStateOf<TrafficUsageOverview?>(null) }
  var error by remember { mutableStateOf(false) }

  LaunchedEffect(reader, days) {
    overview = null
    error = false
    val result = withContext(Dispatchers.IO) { runCatching { reader.read(days) } }
    result.onSuccess { overview = it }.onFailure { error = true }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("通信量の診断") },
    confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text("記録開始以降の通信量です。通信本文のみの概算であり、OSの通信量と一致しません。")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf(7, 30, 90).forEach { option ->
            FilterChip(
              selected = days == option,
              onClick = { days = option },
              label = { Text("${option}日") },
            )
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf("mobile" to "モバイル", "wifi" to "Wi-Fi", "all" to "合計").forEach { (key, name) ->
            FilterChip(
              selected = transport == key,
              onClick = { transport = key },
              label = { Text(name) },
            )
          }
        }
        when {
          error -> Text("通信量の診断を読み込めませんでした")
          overview == null -> Text("集計中…")
          else -> {
            val result = requireNotNull(overview)
            val visible = result.records.filter { transport == "all" || it.transport == transport }
            val counted = visible.sumOf(TrafficUsageRecord::totalBytes)
            Text("機能別の計測合計: ${formatBytes(counted)}")
            val os = when (transport) {
              "mobile" -> result.systemMobileBytes
              "wifi" -> result.systemWifiBytes
              else -> if (result.systemMobileBytes != null && result.systemWifiBytes != null) {
                result.systemMobileBytes + result.systemWifiBytes
              } else null
            }
            if (os == null) {
              Text("OS集計: この端末では取得できません")
            } else {
              Text("OSのアプリ使用量: ${formatBytes(os)}")
              Text("差分（未計測の可能性）: ${formatBytes((os - counted).coerceAtLeast(0L))}")
            }
            if (visible.isEmpty()) Text("計測対象の通信はまだありません")
            visible.groupBy(TrafficUsageRecord::feature)
              .mapValues { (_, rows) -> rows.sumOf(TrafficUsageRecord::totalBytes) }
              .toList().sortedByDescending { it.second }
              .forEach { (feature, bytes) ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text(featureLabel(feature))
                  Text(formatBytes(bytes))
                }
              }
            Text("日別の記録")
            visible.groupBy(TrafficUsageRecord::date)
              .mapValues { (_, rows) -> rows.sumOf(TrafficUsageRecord::totalBytes) }
              .toList().sortedByDescending { it.first }
              .forEach { (date, bytes) ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text(date)
                  Text(formatBytes(bytes))
                }
              }
            if (transport == "all") {
              val unknown = visible.filter { it.transport == "unknown" }.sumOf(TrafficUsageRecord::totalBytes)
              Text("回線不明: ${formatBytes(unknown)}（VPN・回線切替など）")
            }
          }
        }
        Text("動画以外のWebViewや一部の外部通信は機能別計測に含まれない場合があります。記録は端末内に90日間保存されます。")
      }
    },
  )
}

private fun formatBytes(bytes: Long): String = when {
  bytes >= 1_000_000_000L -> String.format(Locale.JAPAN, "%.2f GB", bytes / 1_000_000_000.0)
  bytes >= 1_000_000L -> String.format(Locale.JAPAN, "%.1f MB", bytes / 1_000_000.0)
  bytes >= 1_000L -> String.format(Locale.JAPAN, "%.1f KB", bytes / 1_000.0)
  else -> "$bytes B"
}

private fun featureLabel(feature: String): String = when (feature) {
  "rss" -> "RSS"
  "article" -> "記事"
  "mail" -> "メール"
  "cloud_ai" -> "クラウドAI"
  "wiki" -> "Wiki"
  "podcast" -> "ポッドキャスト"
  "video" -> "動画"
  "library" -> "ライブラリ"
  "model" -> "モデル取得"
  else -> "その他"
}
