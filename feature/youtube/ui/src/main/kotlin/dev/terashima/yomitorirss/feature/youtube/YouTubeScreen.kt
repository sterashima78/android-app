package dev.terashima.yomitorirss.feature.youtube

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.terashima.yomitorirss.core.designsystem.PullToRefreshContainer
import dev.terashima.yomitorirss.core.designsystem.SwipeAction
import dev.terashima.yomitorirss.core.designsystem.SwipeActionListItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun YouTubeScreen(
  modifier: Modifier = Modifier,
  state: YouTubeUiState,
  onSelectTab: (YouTubeTab) -> Unit,
  onRefresh: () -> Unit,
  onSubscribe: (String) -> Unit,
  onUnsubscribe: (YouTubeChannel) -> Unit,
  onMarkRead: (YouTubeVideo) -> Unit,
  onSaveAndRead: (YouTubeVideo) -> Unit,
  onUnsave: (YouTubeVideo) -> Unit,
  onToggleWatchLater: (YouTubeVideo) -> Unit,
  onMarkAllRead: () -> Unit,
  onOpen: (YouTubeVideo) -> Unit,
) {
  var showAddDialog by remember { mutableStateOf(false) }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    bottomBar = {
      NavigationBar(windowInsets = WindowInsets(0, 0, 0, 0)) {
        YouTubeTab.entries.forEach { tab ->
          NavigationBarItem(
            selected = state.selectedTab == tab,
            onClick = { onSelectTab(tab) },
            icon = {
              Icon(
                imageVector = when (tab) {
                  YouTubeTab.UNREAD -> Icons.Default.PlayArrow
                  YouTubeTab.WATCH_LATER -> Icons.Default.WatchLater
                  YouTubeTab.SAVED -> Icons.Default.Bookmark
                  YouTubeTab.SUBSCRIPTIONS -> Icons.Default.Subscriptions
                },
                contentDescription = tab.label,
              )
            },
            label = { Text(tab.label, maxLines = 1) },
          )
        }
      }
    },
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.End,
      ) {
        if (state.selectedTab == YouTubeTab.UNREAD && state.unread.isNotEmpty()) {
          TextButton(onClick = onMarkAllRead) {
            Text("すべて既読")
          }
        }
        if (state.selectedTab == YouTubeTab.SUBSCRIPTIONS) {
          IconButton(onClick = { showAddDialog = true }) {
            Icon(Icons.Default.Add, contentDescription = "チャンネルを追加")
          }
        }
      }

      PullToRefreshContainer(
        modifier = Modifier.weight(1f),
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
      ) {
        when (state.selectedTab) {
          YouTubeTab.UNREAD -> VideoList(
            modifier = Modifier.fillMaxSize(),
            videos = state.unread,
            emptyText = "未読の動画はありません",
            onMarkRead = onMarkRead,
            onSaveAndRead = onSaveAndRead,
            onToggleWatchLater = onToggleWatchLater,
            onOpen = onOpen,
          )

          YouTubeTab.WATCH_LATER -> VideoList(
            modifier = Modifier.fillMaxSize(),
            videos = state.watchLater,
            emptyText = "あとで見る動画はありません",
            onMarkRead = onMarkRead,
            onSaveAndRead = onSaveAndRead,
            onToggleWatchLater = onToggleWatchLater,
            onOpen = onOpen,
          )

          YouTubeTab.SAVED -> SavedVideoList(
            modifier = Modifier.fillMaxSize(),
            videos = state.saved,
            onUnsave = onUnsave,
            onOpen = onOpen,
          )

          YouTubeTab.SUBSCRIPTIONS -> ChannelSubscriptions(
            modifier = Modifier.fillMaxSize(),
            channels = state.channels,
            onUnsubscribe = onUnsubscribe,
          )
        }
      }
    }
  }

  if (showAddDialog) {
    AddYouTubeChannelDialog(
      onDismiss = { showAddDialog = false },
      onAdd = { url ->
        showAddDialog = false
        onSubscribe(url)
      },
    )
  }
}

@Composable
private fun VideoList(
  modifier: Modifier,
  videos: List<YouTubeVideo>,
  emptyText: String,
  onMarkRead: (YouTubeVideo) -> Unit,
  onSaveAndRead: (YouTubeVideo) -> Unit,
  onToggleWatchLater: (YouTubeVideo) -> Unit,
  onOpen: (YouTubeVideo) -> Unit,
) {
  LazyColumn(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    if (videos.isEmpty()) {
      item {
        Text(
          emptyText,
          modifier = Modifier.fillMaxWidth().padding(24.dp),
          style = MaterialTheme.typography.bodyLarge,
        )
      }
    }
    items(videos, key = YouTubeVideo::id) { video ->
      SwipeVideoItem(
        video = video,
        onMarkRead = onMarkRead,
        onSaveAndRead = onSaveAndRead,
        onToggleWatchLater = onToggleWatchLater,
        onOpen = onOpen,
      )
    }
  }
}

@Composable
private fun SavedVideoList(
  modifier: Modifier,
  videos: List<YouTubeVideo>,
  onUnsave: (YouTubeVideo) -> Unit,
  onOpen: (YouTubeVideo) -> Unit,
) {
  LazyColumn(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    if (videos.isEmpty()) {
      item {
        Text(
          "保存済みの動画はありません",
          modifier = Modifier.fillMaxWidth().padding(24.dp),
          style = MaterialTheme.typography.bodyLarge,
        )
      }
    }
    items(videos, key = YouTubeVideo::url) { video ->
      SwipeActionListItem(
        itemKey = video.url,
        left = SwipeAction(
          label = "保存解除",
          color = MaterialTheme.colorScheme.error,
          dismissesItem = false,
          onCommit = { onUnsave(video) },
        ),
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(video) }
            .padding(12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          AsyncImage(
            model = video.thumbnailUrl,
            contentDescription = "${video.title} のサムネイル",
            contentScale = ContentScale.Crop,
            modifier = Modifier
              .width(128.dp)
              .height(72.dp)
              .clip(RoundedCornerShape(8.dp)),
          )
          Column(Modifier.weight(1f)) {
            Text(
              video.title,
              style = MaterialTheme.typography.titleMedium,
              maxLines = 3,
              overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
              video.channelTitle,
              style = MaterialTheme.typography.bodyMedium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
              "保存: ${formatPublishedAt(video.publishedAtEpochMillis)}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun LazyItemScope.SwipeVideoItem(
  video: YouTubeVideo,
  onMarkRead: (YouTubeVideo) -> Unit,
  onSaveAndRead: (YouTubeVideo) -> Unit,
  onToggleWatchLater: (YouTubeVideo) -> Unit,
  onOpen: (YouTubeVideo) -> Unit,
) {
  val left = SwipeAction(
    label = "既読",
    color = MaterialTheme.colorScheme.primary,
    dismissesItem = false,
    onCommit = { onMarkRead(video) },
  )
  val right = SwipeAction(
    label = "保存",
    color = MaterialTheme.colorScheme.tertiary,
    dismissesItem = false,
    onCommit = { onSaveAndRead(video) },
  )
  val farRight = SwipeAction(
    label = if (video.isWatchLater) "未読へ戻す" else "あとで見る",
    color = MaterialTheme.colorScheme.secondary,
    dismissesItem = false,
    onCommit = { onToggleWatchLater(video) },
  )

  SwipeActionListItem(
    itemKey = video.id,
    left = left,
    right = right,
    farRight = farRight,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clickable { onOpen(video) }
        .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      AsyncImage(
        model = video.thumbnailUrl,
        contentDescription = "${video.title} のサムネイル",
        contentScale = ContentScale.Crop,
        modifier = Modifier
          .width(128.dp)
          .height(72.dp)
          .clip(RoundedCornerShape(8.dp)),
      )
      Column(Modifier.weight(1f)) {
        Text(
          video.title,
          style = MaterialTheme.typography.titleMedium,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
          video.channelTitle,
          style = MaterialTheme.typography.bodyMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
          formatPublishedAt(video.publishedAtEpochMillis),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun ChannelSubscriptions(
  modifier: Modifier,
  channels: List<YouTubeChannel>,
  onUnsubscribe: (YouTubeChannel) -> Unit,
) {
  LazyColumn(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    if (channels.isEmpty()) {
      item {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
          Text("購読中のYouTubeチャンネルはありません", style = MaterialTheme.typography.bodyLarge)
          Spacer(Modifier.height(8.dp))
          Text(
            "右上の追加ボタンからチャンネルURLを入力してください。",
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      }
    }
    items(channels, key = YouTubeChannel::id) { channel ->
      Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Column(Modifier.weight(1f)) {
            Text(channel.title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
              channel.url,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          IconButton(onClick = { onUnsubscribe(channel) }) {
            Icon(Icons.Default.Delete, contentDescription = "購読解除")
          }
        }
      }
    }
  }
}

@Composable
private fun AddYouTubeChannelDialog(
  onDismiss: () -> Unit,
  onAdd: (String) -> Unit,
) {
  var value by remember { mutableStateOf("") }
  val uriHandler = LocalUriHandler.current
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("YouTubeチャンネルを購読") },
    text = {
      Column {
        Text("チャンネルURLを入力してください。")
        Spacer(Modifier.height(8.dp))
        Text(
          "チャンネルIDは以下の外部サイトなどで確認できます。",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          YOUTUBE_CHANNEL_ID_FINDER_URL,
          modifier = Modifier.clickable { uriHandler.openUri(YOUTUBE_CHANNEL_ID_FINDER_URL) },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
          value = value,
          onValueChange = { value = it },
          singleLine = true,
          label = { Text("https://www.youtube.com/channel/UC...") },
        )
      }
    },
    confirmButton = {
      TextButton(
        onClick = { onAdd(value.trim()) },
        enabled = value.isNotBlank(),
      ) {
        Text("購読")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("キャンセル")
      }
    },
  )
}

private const val YOUTUBE_CHANNEL_ID_FINDER_URL =
  "https://www.ytultra.com/ja/youtube-channel-id-finder/"

private val publishedAtFormatter: DateTimeFormatter =
  DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

private fun formatPublishedAt(epochMillis: Long): String =
  Instant.ofEpochMilli(epochMillis)
    .atZone(ZoneId.systemDefault())
    .format(publishedAtFormatter)
