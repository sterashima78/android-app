package dev.terashima.yomitorirss.feature.video.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.terashima.yomitorirss.core.designsystem.SwipeAction
import dev.terashima.yomitorirss.core.designsystem.SwipeActionListItem
import dev.terashima.yomitorirss.feature.video.VideoItem

internal enum class VideoUnwatchedLayout {
  GRID,
  LIST,
}

@Composable
internal fun VideoUnwatchedLayoutSelector(
  selected: VideoUnwatchedLayout,
  onSelected: (VideoUnwatchedLayout) -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 12.dp, vertical = 2.dp),
    horizontalArrangement = Arrangement.End,
  ) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      FilterChip(
        selected = selected == VideoUnwatchedLayout.GRID,
        onClick = { onSelected(VideoUnwatchedLayout.GRID) },
        label = { Text("グリッド") },
      )
      FilterChip(
        selected = selected == VideoUnwatchedLayout.LIST,
        onClick = { onSelected(VideoUnwatchedLayout.LIST) },
        label = { Text("リスト") },
      )
    }
  }
}

@Composable
internal fun VideoUnwatchedList(
  items: List<VideoItem>,
  onPlay: (VideoItem) -> Unit,
  onSaveVideo: (VideoItem, String?) -> Unit,
  onSetCompleted: (VideoItem, Boolean) -> Unit,
) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(vertical = 6.dp),
  ) {
    items(items, key = VideoItem::id) { item ->
      SwipeActionListItem(
        itemKey = item.id,
        left = SwipeAction(
          label = "視聴済み",
          color = MaterialTheme.colorScheme.primary,
          onCommit = { onSetCompleted(item, true) },
        ),
        right = SwipeAction(
          label = "保存",
          color = MaterialTheme.colorScheme.secondary,
          onCommit = { onSaveVideo(item, null) },
        ),
      ) {
        VideoUnwatchedListRow(
          item = item,
          onPlay = { onPlay(item) },
        )
      }
    }
  }
}

@Composable
private fun VideoUnwatchedListRow(
  item: VideoItem,
  onPlay: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onPlay)
      .padding(horizontal = 12.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier = Modifier
        .width(128.dp)
        .aspectRatio(16f / 9f)
        .background(MaterialTheme.colorScheme.surfaceVariant),
      contentAlignment = Alignment.Center,
    ) {
      Text("サムネイルなし", style = MaterialTheme.typography.labelSmall)
      item.thumbnailUrl?.takeIf(String::isNotBlank)?.let { thumbnail ->
        AsyncImage(
          model = thumbnail,
          contentDescription = "${item.title} のサムネイル",
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
      }
      val playback = item.playbackState
      if (playback != null && playback.durationMs > 0L && !playback.completed) {
        val ratio = (playback.positionMs.toFloat() / playback.durationMs.toFloat()).coerceIn(0f, 1f)
        if (ratio > 0f) {
          Box(
            modifier = Modifier
              .fillMaxWidth(ratio)
              .height(4.dp)
              .align(Alignment.BottomStart)
              .background(MaterialTheme.colorScheme.primary),
          )
        }
      }
    }
    Spacer(Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        item.title,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
      )
      item.playbackState?.takeIf { it.positionMs > 0L }?.let { playback ->
        Text(
          formatListPlaybackPosition(playback.positionMs, playback.durationMs),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

private fun formatListPlaybackPosition(positionMs: Long, durationMs: Long): String {
  fun format(value: Long): String {
    val totalSeconds = value.coerceAtLeast(0L) / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
      "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
      "%d:%02d".format(minutes, seconds)
    }
  }
  return if (durationMs > 0L) "${format(positionMs)} / ${format(durationMs)}" else format(positionMs)
}
