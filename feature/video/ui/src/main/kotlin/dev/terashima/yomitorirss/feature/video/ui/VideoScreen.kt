package dev.terashima.yomitorirss.feature.video.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.terashima.yomitorirss.feature.video.VideoFolder
import dev.terashima.yomitorirss.feature.video.VideoItem
import dev.terashima.yomitorirss.feature.video.VideoSource
import dev.terashima.yomitorirss.feature.video.WebVideoExtractorRule

private enum class VideoTab(val label: String) {
  ALL("未視聴"),
  CONTINUE("続き"),
  SAVED("保存済み"),
  COMPLETED("視聴済み"),
  SETTINGS("設定"),
}

@Composable
fun VideoScreen(
  state: VideoUiState,
  onPlay: (VideoItem) -> Unit,
  onEnsureThumbnail: (VideoItem) -> Unit,
  onAddWeb: (String) -> Unit,
  onRefreshSmb: () -> Unit,
  onRemove: (VideoItem) -> Unit,
  onSetCompleted: (VideoItem, Boolean) -> Unit,
  onSaveVideo: (VideoItem, String?) -> Unit,
  onRemoveSavedVideo: (VideoItem) -> Unit,
  onSaveFolder: (VideoFolder) -> Unit,
  onDeleteFolder: (String) -> Unit,
  onSaveExtractorRule: (WebVideoExtractorRule) -> Unit,
  onDeleteExtractorRule: (String) -> Unit,
  onDismissMessage: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val snackbar = remember { SnackbarHostState() }
  var tabName by rememberSaveable { mutableStateOf(VideoTab.ALL.name) }
  var sourceName by rememberSaveable { mutableStateOf<String?>(null) }
  var unwatchedLayoutName by rememberSaveable { mutableStateOf(VideoUnwatchedLayout.GRID.name) }
  var savedLocation by remember { mutableStateOf<VideoSavedBrowserLocation>(VideoSavedBrowserLocation.Root) }
  var addWebVisible by remember { mutableStateOf(false) }
  var editingRule by remember { mutableStateOf<WebVideoExtractorRule?>(null) }
  var newRuleVisible by remember { mutableStateOf(false) }
  var editingFolder by remember { mutableStateOf<VideoFolder?>(null) }
  var newFolderVisible by remember { mutableStateOf(false) }
  val tab = VideoTab.valueOf(tabName)
  val source = sourceName?.let { selected -> VideoSource.entries.firstOrNull { it.name == selected } }
  val unwatchedLayout = VideoUnwatchedLayout.valueOf(unwatchedLayoutName)

  LaunchedEffect(state.message) {
    val message = state.message ?: return@LaunchedEffect
    snackbar.showSnackbar(message)
    onDismissMessage()
  }

  LaunchedEffect(source) {
    savedLocation = VideoSavedBrowserLocation.Root
  }

  if (addWebVisible) {
    AddWebVideoDialog(
      busy = state.busy,
      onDismiss = { addWebVisible = false },
      onAdd = { url ->
        addWebVisible = false
        onAddWeb(url)
      },
    )
  }

  if (newRuleVisible || editingRule != null) {
    WebVideoExtractorRuleDialog(
      initial = editingRule,
      busy = state.busy,
      onDismiss = {
        editingRule = null
        newRuleVisible = false
      },
      onSave = { rule ->
        editingRule = null
        newRuleVisible = false
        onSaveExtractorRule(rule)
      },
    )
  }

  if (newFolderVisible || editingFolder != null) {
    VideoFolderDialog(
      initial = editingFolder,
      busy = state.busy,
      onDismiss = {
        editingFolder = null
        newFolderVisible = false
      },
      onSave = { folder ->
        editingFolder = null
        newFolderVisible = false
        onSaveFolder(folder)
      },
    )
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    snackbarHost = { SnackbarHost(snackbar) },
    floatingActionButton = {
      if (tab != VideoTab.SETTINGS && !state.busy) {
        FloatingActionButton(onClick = { addWebVisible = true }) {
          Icon(Icons.Default.Add, contentDescription = "Web動画を追加")
        }
      }
    },
    bottomBar = {
      NavigationBar(windowInsets = WindowInsets(0, 0, 0, 0)) {
        VideoTab.entries.forEach { item ->
          NavigationBarItem(
            selected = tab == item,
            onClick = { tabName = item.name },
            icon = {
              Icon(
                imageVector = when (item) {
                  VideoTab.ALL -> Icons.Default.List
                  VideoTab.CONTINUE -> Icons.Default.PlayCircle
                  VideoTab.SAVED -> Icons.Default.Folder
                  VideoTab.COMPLETED -> Icons.Default.CheckCircle
                  VideoTab.SETTINGS -> Icons.Default.Settings
                },
                contentDescription = item.label,
              )
            },
            label = { Text(item.label, maxLines = 1) },
          )
        }
      }
    },
  ) { padding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding),
    ) {
      if (state.loading) {
        CircularProgressIndicator(Modifier.align(Alignment.Center))
      } else if (tab == VideoTab.SETTINGS) {
        VideoSettings(
          state = state,
          onRefreshSmb = onRefreshSmb,
          onNewFolder = { newFolderVisible = true },
          onEditFolder = { editingFolder = it },
          onDeleteFolder = onDeleteFolder,
          onNewRule = { newRuleVisible = true },
          onEditRule = { editingRule = it },
          onDeleteRule = onDeleteExtractorRule,
        )
      } else {
        Column(Modifier.fillMaxSize()) {
          VideoSourceFilters(
            selected = source,
            onSelected = { sourceName = it?.name },
          )
          if (tab == VideoTab.ALL) {
            VideoUnwatchedLayoutSelector(
              selected = unwatchedLayout,
              onSelected = { unwatchedLayoutName = it.name },
            )
          }
          if (tab == VideoTab.SAVED) {
            VideoSavedBrowser(
              state = state,
              source = source,
              location = savedLocation,
              onLocationChange = { savedLocation = it },
              onPlay = onPlay,
              onEnsureThumbnail = onEnsureThumbnail,
              onRemove = onRemove,
              onSetCompleted = onSetCompleted,
              onSaveVideo = onSaveVideo,
              onRemoveSavedVideo = onRemoveSavedVideo,
            )
          } else {
            val filtered = remember(state.items, tab, source) {
              state.items.filter { item ->
                val tabMatches = when (tab) {
                  VideoTab.ALL -> item.isUnwatched()
                  VideoTab.CONTINUE -> item.playbackState?.let { it.positionMs > 0L && !it.completed } == true
                  VideoTab.SAVED -> false
                  VideoTab.COMPLETED -> item.playbackState?.completed == true
                  VideoTab.SETTINGS -> false
                }
                (source == null || item.source == source) && tabMatches
              }
            }
            if (filtered.isEmpty()) {
              Text(
                when (tab) {
                  VideoTab.ALL -> "未視聴の動画はありません。"
                  VideoTab.CONTINUE -> "再生途中の動画はありません。"
                  VideoTab.SAVED -> ""
                  VideoTab.COMPLETED -> "視聴済みの動画はありません。"
                  VideoTab.SETTINGS -> ""
                },
                modifier = Modifier.padding(24.dp),
                style = MaterialTheme.typography.bodyMedium,
              )
            } else if (tab == VideoTab.ALL && unwatchedLayout == VideoUnwatchedLayout.LIST) {
              VideoUnwatchedList(
                items = filtered,
                onPlay = onPlay,
                onSaveVideo = onSaveVideo,
                onSetCompleted = onSetCompleted,
              )
            } else {
              VideoGrid(
                items = filtered,
                folders = state.folders,
                onPlay = onPlay,
                onEnsureThumbnail = onEnsureThumbnail,
                onRemove = onRemove,
                onSetCompleted = onSetCompleted,
                onSaveVideo = onSaveVideo,
                onRemoveSavedVideo = onRemoveSavedVideo,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun VideoSourceFilters(
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
private fun VideoSavedBrowser(
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

@Composable
private fun VideoGrid(
  items: List<VideoItem>,
  folders: List<VideoFolder>,
  onPlay: (VideoItem) -> Unit,
  onEnsureThumbnail: (VideoItem) -> Unit,
  onRemove: (VideoItem) -> Unit,
  onSetCompleted: (VideoItem, Boolean) -> Unit,
  onSaveVideo: (VideoItem, String?) -> Unit,
  onRemoveSavedVideo: (VideoItem) -> Unit,
) {
  LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = 160.dp),
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(12.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    items(items, key = VideoItem::id) { item ->
      VideoCard(
        item = item,
        folders = folders,
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

@Composable
private fun VideoCard(
  item: VideoItem,
  folders: List<VideoFolder>,
  onPlay: () -> Unit,
  onEnsureThumbnail: () -> Unit,
  onRemove: () -> Unit,
  onSetCompleted: (Boolean) -> Unit,
  onSave: (String?) -> Unit,
  onRemoveSaved: () -> Unit,
) {
  var menuExpanded by remember(item.id) { mutableStateOf(false) }
  var destinationVisible by remember(item.id) { mutableStateOf(false) }
  val folderName = item.savedState?.folderId?.let { folderId -> folders.firstOrNull { it.id == folderId }?.name }

  LaunchedEffect(item.id, item.thumbnailUrl) {
    if (item.source == VideoSource.SMB && item.thumbnailUrl.isNullOrBlank()) {
      onEnsureThumbnail()
    }
  }

  if (destinationVisible) {
    SaveDestinationDialog(
      folders = folders,
      currentFolderId = item.savedState?.folderId,
      onDismiss = { destinationVisible = false },
      onSelect = { folderId ->
        destinationVisible = false
        onSave(folderId)
      },
    )
  }

  Column(Modifier.fillMaxWidth()) {
    Box {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .aspectRatio(16f / 9f)
          .combinedClickable(
            onClick = onPlay,
            onLongClick = { menuExpanded = true },
          ),
      ) {
        Box(
          modifier = Modifier
            .fillMaxSize()
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
      }
      DropdownMenu(
        expanded = menuExpanded,
        onDismissRequest = { menuExpanded = false },
      ) {
        when (item.source) {
          VideoSource.SMB -> Unit
          VideoSource.WEB -> {
            DropdownMenuItem(
              text = { Text(if (item.savedState?.folderId == null) "フォルダへ移動" else "保存先を変更") },
              onClick = {
                menuExpanded = false
                destinationVisible = true
              },
            )
            if (item.savedState?.folderId != null) {
              DropdownMenuItem(
                text = { Text("未分類へ移動") },
                onClick = {
                  menuExpanded = false
                  onRemoveSaved()
                },
              )
            }
          }
          VideoSource.SERVICE -> {
            if (item.savedState == null) {
              DropdownMenuItem(
                text = { Text("保存") },
                onClick = {
                  menuExpanded = false
                  onSave(null)
                },
              )
              DropdownMenuItem(
                text = { Text("フォルダに保存") },
                onClick = {
                  menuExpanded = false
                  destinationVisible = true
                },
              )
            } else {
              DropdownMenuItem(
                text = { Text("保存先を変更") },
                onClick = {
                  menuExpanded = false
                  destinationVisible = true
                },
              )
              DropdownMenuItem(
                text = { Text("保存解除") },
                onClick = {
                  menuExpanded = false
                  onRemoveSaved()
                },
              )
            }
          }
        }
        val completed = item.playbackState?.completed == true
        DropdownMenuItem(
          text = { Text(if (completed) "未視聴に戻す" else "視聴済みにする") },
          onClick = {
            menuExpanded = false
            onSetCompleted(!completed)
          },
        )
        if (item.source != VideoSource.SERVICE) {
          DropdownMenuItem(
            text = { Text("一覧から削除") },
            onClick = {
              menuExpanded = false
              onRemove()
            },
          )
        }
      }
    }
    Spacer(Modifier.height(6.dp))
    Text(
      buildString {
        append(
          when (item.source) {
            VideoSource.SMB -> "SMB"
            VideoSource.WEB -> "Web"
            VideoSource.SERVICE -> "サービス"
          },
        )
        if (item.isSaved) {
          append(" ・ 保存済み")
          if (item.source != VideoSource.SMB) {
            if (folderName != null) append(" / $folderName")
            else append(" / 未分類")
          }
        }
      },
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.primary,
    )
    Text(
      item.title,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.Medium,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
    item.playbackState?.let { playback ->
      Text(
        if (playback.completed) "視聴済み" else formatPlaybackPosition(playback.positionMs, playback.durationMs),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun SaveDestinationDialog(
  folders: List<VideoFolder>,
  currentFolderId: String?,
  onDismiss: () -> Unit,
  onSelect: (String?) -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("整理先を選択") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        TextButton(
          onClick = { onSelect(null) },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(if (currentFolderId == null) "未分類 ✓" else "未分類")
        }
        folders.forEach { folder ->
          TextButton(
            onClick = { onSelect(folder.id) },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(if (currentFolderId == folder.id) "${folder.name} ✓" else folder.name)
          }
        }
        if (folders.isEmpty()) {
          Text(
            "フォルダは設定画面から作成できます。",
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
    },
    confirmButton = {},
    dismissButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
  )
}

@Composable
private fun VideoSettings(
  state: VideoUiState,
  onRefreshSmb: () -> Unit,
  onNewFolder: () -> Unit,
  onEditFolder: (VideoFolder) -> Unit,
  onDeleteFolder: (String) -> Unit,
  onNewRule: () -> Unit,
  onEditRule: (WebVideoExtractorRule) -> Unit,
  onDeleteRule: (String) -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Text("整理フォルダ", style = MaterialTheme.typography.titleMedium)
    Text(
      "Web動画と明示保存した購読動画は、保存済み画面のフォルダへ整理できます。SMB動画は同期元のディレクトリ階層をそのまま辿ります。",
      style = MaterialTheme.typography.bodySmall,
    )
    Button(onClick = onNewFolder, enabled = !state.busy) {
      Text("フォルダを追加")
    }
    state.folders.forEach { folder ->
      Card(Modifier.fillMaxWidth()) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(folder.name, fontWeight = FontWeight.Medium)
          Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { onEditFolder(folder) }) { Text("名前変更") }
            TextButton(onClick = { onDeleteFolder(folder.id) }) { Text("削除") }
          }
        }
      }
    }

    Text("SMB", style = MaterialTheme.typography.titleMedium)
    Text(
      "全体設定のSMB接続を利用し、動画側で同期する共有・パスを設定します。動画側にはパスワードを保存しません。",
      style = MaterialTheme.typography.bodySmall,
    )
    Button(onClick = onRefreshSmb, enabled = !state.busy) {
      if (state.busy) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
      } else {
        Text("SMB動画の設定・同期")
      }
    }

    Text("Web抽出ルール", style = MaterialTheme.typography.titleMedium)
    Text(
      "既定ではOGPからタイトルとサムネイルを取得します。必要なサイトだけWebViewスクリプトで上書きし、再生URLも抽出できます。",
      style = MaterialTheme.typography.bodySmall,
    )
    Button(onClick = onNewRule, enabled = !state.busy) {
      Text("抽出ルールを追加")
    }
    state.extractorRules.forEach { rule ->
      Card(Modifier.fillMaxWidth()) {
        Column(
          modifier = Modifier.padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(rule.urlPattern, fontWeight = FontWeight.Medium)
          Text(
            buildList {
              if (!rule.titleExtractorCode.isNullOrBlank()) add("タイトル")
              if (!rule.thumbnailExtractorCode.isNullOrBlank()) add("サムネイル")
              if (!rule.playbackExtractorCode.isNullOrBlank()) add("再生URL")
            }.joinToString(" / "),
            style = MaterialTheme.typography.labelSmall,
          )
          if (rule.shareCookiesForPlayback) {
            Text(
              "再生時Cookie共有: 有効",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
            )
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onEditRule(rule) }) { Text("編集") }
            TextButton(onClick = { onDeleteRule(rule.id) }) { Text("削除") }
          }
        }
      }
    }
  }
}

@Composable
private fun VideoFolderDialog(
  initial: VideoFolder?,
  busy: Boolean,
  onDismiss: () -> Unit,
  onSave: (VideoFolder) -> Unit,
) {
  var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (initial == null) "フォルダを追加" else "フォルダ名を変更") },
    text = {
      OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("フォルダ名") },
        singleLine = true,
      )
    },
    confirmButton = {
      TextButton(
        onClick = {
          onSave(
            VideoFolder(
              id = initial?.id.orEmpty(),
              name = name.trim(),
              createdAtEpochMillis = initial?.createdAtEpochMillis ?: 0L,
              updatedAtEpochMillis = initial?.updatedAtEpochMillis ?: 0L,
            ),
          )
        },
        enabled = name.isNotBlank() && !busy,
      ) { Text("保存") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
  )
}

@Composable
private fun AddWebVideoDialog(
  busy: Boolean,
  onDismiss: () -> Unit,
  onAdd: (String) -> Unit,
) {
  var url by remember { mutableStateOf("") }
  val valid = url.trim().startsWith("http://") || url.trim().startsWith("https://")
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Web動画を追加") },
    text = {
      OutlinedTextField(
        value = url,
        onValueChange = { url = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("URL") },
        singleLine = true,
      )
    },
    confirmButton = {
      TextButton(onClick = { onAdd(url.trim()) }, enabled = valid && !busy) { Text("追加") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
  )
}

@Composable
private fun WebVideoExtractorRuleDialog(
  initial: WebVideoExtractorRule?,
  busy: Boolean,
  onDismiss: () -> Unit,
  onSave: (WebVideoExtractorRule) -> Unit,
) {
  var pattern by remember(initial?.id) { mutableStateOf(initial?.urlPattern.orEmpty()) }
  var titleCode by remember(initial?.id) { mutableStateOf(initial?.titleExtractorCode.orEmpty()) }
  var thumbnailCode by remember(initial?.id) { mutableStateOf(initial?.thumbnailExtractorCode.orEmpty()) }
  var playbackCode by remember(initial?.id) { mutableStateOf(initial?.playbackExtractorCode.orEmpty()) }
  var shareCookiesForPlayback by remember(initial?.id) {
    mutableStateOf(initial?.shareCookiesForPlayback ?: false)
  }
  var timeout by remember(initial?.id) { mutableStateOf((initial?.timeoutSeconds ?: 15).toString()) }
  val timeoutValue = timeout.toIntOrNull()
  val valid = pattern.startsWith("https://") &&
    timeoutValue != null && timeoutValue in 1..60 &&
    listOf(titleCode, thumbnailCode, playbackCode).any(String::isNotBlank)

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (initial == null) "抽出ルールを追加" else "抽出ルールを編集") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = pattern,
          onValueChange = { pattern = it },
          label = { Text("URLパターン") },
          placeholder = { Text("https://example.com/videos/*") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
        )
        OutlinedTextField(
          value = titleCode,
          onValueChange = { titleCode = it },
          label = { Text("タイトル抽出 JavaScript（任意）") },
          modifier = Modifier.fillMaxWidth(),
          minLines = 3,
        )
        OutlinedTextField(
          value = thumbnailCode,
          onValueChange = { thumbnailCode = it },
          label = { Text("サムネイル抽出 JavaScript（任意）") },
          modifier = Modifier.fillMaxWidth(),
          minLines = 3,
        )
        OutlinedTextField(
          value = playbackCode,
          onValueChange = { playbackCode = it },
          label = { Text("再生URL抽出 JavaScript（任意）") },
          modifier = Modifier.fillMaxWidth(),
          minLines = 3,
        )
        Text(
          "各関数は async ({ url }) => ({ ... }) の形式でPromiseを返します。",
          style = MaterialTheme.typography.labelSmall,
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Checkbox(
            checked = shareCookiesForPlayback,
            onCheckedChange = { shareCookiesForPlayback = it },
          )
          Column(modifier = Modifier.padding(start = 4.dp)) {
            Text("再生時にWebViewのCookieを共有する")
            Text(
              "Cookieが必要なstreamだけで有効にしてください。Cookie値は保存せず、再生中のHTTP requestにだけ利用します。",
              style = MaterialTheme.typography.labelSmall,
            )
          }
        }
        OutlinedTextField(
          value = timeout,
          onValueChange = { timeout = it.filter(Char::isDigit) },
          label = { Text("タイムアウト秒") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
        )
      }
    },
    confirmButton = {
      TextButton(
        enabled = valid && !busy,
        onClick = {
          onSave(
            WebVideoExtractorRule(
              id = initial?.id.orEmpty(),
              urlPattern = pattern.trim(),
              titleExtractorCode = titleCode.trim().takeIf(String::isNotBlank),
              thumbnailExtractorCode = thumbnailCode.trim().takeIf(String::isNotBlank),
              playbackExtractorCode = playbackCode.trim().takeIf(String::isNotBlank),
              timeoutSeconds = requireNotNull(timeoutValue),
              updatedAtEpochMillis = initial?.updatedAtEpochMillis ?: 0L,
              shareCookiesForPlayback = shareCookiesForPlayback,
            ),
          )
        },
      ) { Text("保存") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
  )
}

private fun formatPlaybackPosition(positionMs: Long, durationMs: Long): String {
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

internal fun VideoItem.isUnwatched(): Boolean {
  return !isSaved && playbackState?.completed != true
}
