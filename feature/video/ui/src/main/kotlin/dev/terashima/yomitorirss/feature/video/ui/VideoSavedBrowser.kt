package dev.terashima.yomitorirss.feature.video.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.terashima.yomitorirss.feature.video.VideoItem
import dev.terashima.yomitorirss.feature.video.VideoSource

@Composable
internal fun VideoSourceFilters(
  selected: VideoSource?,
  onSelected: (VideoSource?) -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .horizontalScroll(rememberScrollState())
      .padding(horizontal = 12.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    FilterChip(
      selected = selected == null,
      onClick = { onSelected(null) },
      label = { Text("すべて") },
    )
    VideoSource.entries.forEach { source ->
      FilterChip(
        selected = selected == source,
        onClick = { onSelected(source) },
        label = {
          Text(
            when (source) {
              VideoSource.SMB -> "SMB"
              VideoSource.WEB -> "Web"
              VideoSource.SERVICE -> "サービス"
            },
          )
        },
      )
    }
  }
}

@Composable
internal fun VideoSavedBrowser(
  state: VideoUiState,
  source: VideoSource?,
  location: VideoSavedBrowserLocation,
  onLocationChange: (VideoSavedBrowserLocation) -> Unit,
  onPlay: (VideoItem) -> Unit,
  onEnsureThumbnail: (VideoItem) -> Unit,
  onRemove: (VideoItem) -> Unit,
  onSetCompleted: (VideoItem, Boolean) -> Unit,
  onSaveVideo: (VideoItem, String?) -> Unit,
  onRemoveSavedVideo: (VideoItem) -> Unit,
) {
  val content = remember(state.items, state.folders, state.smbSources, source, location) {
    buildVideoSavedBrowserContent(
      items = state.items,
      folders = state.folders,
      smbSources = state.smbSources,
      location = location,
      sourceFilter = source,
    )
  }

  Column(Modifier.fillMaxSize()) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(horizontal = 8.dp, vertical = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      content.breadcrumbs.forEachIndexed { index, breadcrumb ->
        if (index > 0) Text("/")
        TextButton(
          onClick = { onLocationChange(breadcrumb.location) },
          enabled = index != content.breadcrumbs.lastIndex,
        ) {
          Text(breadcrumb.label, maxLines = 1)
        }
      }
    }

    if (content.directories.isEmpty() && content.videos.isEmpty()) {
      Text(
        if (location == VideoSavedBrowserLocation.Root) {
          "保存済み動画はありません。SMB動画を同期するか、Web動画を追加するとここに表示されます。"
        } else {
          "このフォルダには動画がありません。"
        },
        modifier = Modifier.padding(24.dp),
        style = MaterialTheme.typography.bodyMedium,
      )
    } else {
      LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        items(content.directories, key = VideoSavedDirectoryEntry::key) { directory ->
          SavedDirectoryCard(
            entry = directory,
            onClick = { onLocationChange(directory.location) },
          )
        }
        items(content.videos, key = VideoItem::id) { item ->
          VideoCard(
            item = item,
            folders = state.folders,
            onPlay = { onPlay(item) },
            onEnsureThumbnail = { onEnsureThumbnail(item) },
            onRemove = { onRemove(item) },
            onSetCompleted = { completed -> onSetCompleted(item, completed) },
            onSave = { folderId -> onSaveVideo(item, folderId) },
            onRemoveSaved = { onRemoveSavedVideo(item) },
          )
        }
      }
    }
  }
}

@Composable
private fun SavedDirectoryCard(
  entry: VideoSavedDirectoryEntry,
  onClick: () -> Unit,
) {
  Column(Modifier.fillMaxWidth()) {
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(16f / 9f)
        .clickable(onClick = onClick),
    ) {
      Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        Icon(
          Icons.Default.Folder,
          contentDescription = null,
          modifier = Modifier.size(48.dp),
        )
      }
    }
    Spacer(Modifier.height(6.dp))
    Text(
      entry.name,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.Medium,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
  }
}
