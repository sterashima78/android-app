package dev.terashima.yomitorirss.feature.video.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.terashima.yomitorirss.feature.video.VideoProviderVideo

private enum class VideoProviderInboxTab(val label: String) {
  UNREAD("未読"),
  WATCH_LATER("あとで見る"),
}

@Composable
internal fun VideoProviderInboxDialog(
  state: VideoUiState,
  onPlay: (VideoProviderVideo) -> Unit,
  onMarkRead: (VideoProviderVideo) -> Unit,
  onSetWatchLater: (VideoProviderVideo, Boolean) -> Unit,
  onMarkAllRead: () -> Unit,
  onDismiss: () -> Unit,
) {
  var selectedName by rememberSaveable { mutableStateOf(VideoProviderInboxTab.UNREAD.name) }
  val selected = VideoProviderInboxTab.valueOf(selectedName)
  val items = when (selected) {
    VideoProviderInboxTab.UNREAD -> state.unreadProviderVideos
    VideoProviderInboxTab.WATCH_LATER -> state.watchLaterProviderVideos
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("購読動画") },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          VideoProviderInboxTab.entries.forEach { tab ->
            val count = when (tab) {
              VideoProviderInboxTab.UNREAD -> state.unreadProviderVideos.size
              VideoProviderInboxTab.WATCH_LATER -> state.watchLaterProviderVideos.size
            }
            FilterChip(
              selected = selected == tab,
              onClick = { selectedName = tab.name },
              label = { Text("${tab.label} $count") },
            )
          }
        }

        if (selected == VideoProviderInboxTab.UNREAD && items.isNotEmpty()) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
          ) {
            TextButton(
              onClick = onMarkAllRead,
              enabled = !state.busy,
            ) {
              Text("すべて既読")
            }
          }
        }

        if (items.isEmpty()) {
          Text(
            text = when (selected) {
              VideoProviderInboxTab.UNREAD -> "未読の購読動画はありません。"
              VideoProviderInboxTab.WATCH_LATER -> "あとで見る動画はありません。"
            },
            style = MaterialTheme.typography.bodyMedium,
          )
        } else {
          items.forEach { item ->
            ProviderInboxItem(
              item = item,
              watchLater = selected == VideoProviderInboxTab.WATCH_LATER,
              busy = state.busy,
              onPlay = { onPlay(item) },
              onMarkRead = { onMarkRead(item) },
              onSetWatchLater = { value -> onSetWatchLater(item, value) },
            )
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

@Composable
private fun ProviderInboxItem(
  item: VideoProviderVideo,
  watchLater: Boolean,
  busy: Boolean,
  onPlay: () -> Unit,
  onMarkRead: () -> Unit,
  onSetWatchLater: (Boolean) -> Unit,
) {
  Card(Modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = item.video.title,
        fontWeight = FontWeight.SemiBold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      item.subscriptionTitle?.takeIf(String::isNotBlank)?.let { title ->
        Text(
          text = title,
          style = MaterialTheme.typography.bodySmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TextButton(onClick = onPlay, enabled = !busy) {
          Text("開く")
        }
        if (watchLater) {
          TextButton(onClick = { onSetWatchLater(false) }, enabled = !busy) {
            Text("未読へ戻す")
          }
        } else {
          TextButton(onClick = { onSetWatchLater(true) }, enabled = !busy) {
            Text("あとで見る")
          }
          TextButton(onClick = onMarkRead, enabled = !busy) {
            Text("既読")
          }
        }
      }
    }
  }
}
