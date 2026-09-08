package dev.terashima.yomitorirss.feature.video.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.terashima.yomitorirss.feature.video.VideoProvider
import dev.terashima.yomitorirss.feature.video.VideoProviderType
import dev.terashima.yomitorirss.feature.video.VideoProviderVideo

@Composable
internal fun VideoProviderSettingsDialog(
  state: VideoUiState,
  onSaveProvider: (VideoProvider) -> Unit,
  onDeleteProvider: (String) -> Unit,
  onSubscribe: (String, String) -> Unit,
  onUnsubscribe: (String) -> Unit,
  onRefresh: (String?) -> Unit,
  onMarkRead: (VideoProviderVideo) -> Unit,
  onDismiss: () -> Unit,
) {
  var sourceUrl by remember { mutableStateOf("") }
  val configuredTypes = state.providers.mapTo(HashSet()) { it.type }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("購読プロバイダ設定") },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          "Web動画は1件ずつ登録します。ここではチャンネルなどを購読し、新着動画を未読として取り込むプロバイダを管理します。",
        )

        VideoProviderType.entries
          .filterNot(configuredTypes::contains)
          .forEach { type ->
            Button(
              onClick = {
                onSaveProvider(
                  VideoProvider(
                    id = "",
                    type = type,
                    name = type.displayName(),
                    enabled = true,
                  ),
                )
              },
              enabled = !state.busy,
            ) {
              Text("${type.displayName()} を追加")
            }
          }

        state.providers.forEach { provider ->
          HorizontalDivider()
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(Modifier.weight(1f)) {
              Text(provider.name, fontWeight = FontWeight.SemiBold)
              Text(if (provider.enabled) "有効" else "無効")
            }
            TextButton(
              onClick = { onSaveProvider(provider.copy(enabled = !provider.enabled)) },
              enabled = !state.busy,
            ) {
              Text(if (provider.enabled) "無効化" else "有効化")
            }
            TextButton(
              onClick = { onDeleteProvider(provider.id) },
              enabled = !state.busy,
            ) {
              Text("削除")
            }
          }

          OutlinedTextField(
            value = sourceUrl,
            onValueChange = { sourceUrl = it },
            modifier = Modifier.fillMaxWidth(),
            enabled = provider.enabled && !state.busy,
            singleLine = true,
            label = { Text("チャンネルURL") },
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
              onClick = {
                val value = sourceUrl.trim()
                if (value.isNotEmpty()) {
                  onSubscribe(provider.id, value)
                  sourceUrl = ""
                }
              },
              enabled = provider.enabled && sourceUrl.isNotBlank() && !state.busy,
            ) {
              Text("購読を追加")
            }
            TextButton(
              onClick = { onRefresh(provider.id) },
              enabled = provider.enabled && !state.busy,
            ) {
              Text("更新")
            }
          }

          val subscriptions = state.subscriptions.filter { it.providerId == provider.id }
          if (subscriptions.isEmpty()) {
            Text("購読中のチャンネルはありません")
          } else {
            subscriptions.forEach { subscription ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(Modifier.weight(1f)) {
                  Text(subscription.title)
                  Text(subscription.sourceUrl)
                }
                TextButton(
                  onClick = { onUnsubscribe(subscription.id) },
                  enabled = !state.busy,
                ) {
                  Text("解除")
                }
              }
            }
          }
        }

        HorizontalDivider()
        Text("未読動画 ${state.unreadProviderVideos.size}件", fontWeight = FontWeight.SemiBold)
        state.unreadProviderVideos.take(20).forEach { item ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(Modifier.weight(1f)) {
              Text(item.video.title)
              item.subscriptionTitle?.takeIf(String::isNotBlank)?.let { Text(it) }
            }
            TextButton(
              onClick = { onMarkRead(item) },
              enabled = !state.busy,
            ) {
              Text("既読")
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("閉じる")
      }
    },
  )
}

private fun VideoProviderType.displayName(): String = when (this) {
  VideoProviderType.YOUTUBE -> "YouTube"
}
