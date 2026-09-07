package dev.terashima.yomitorirss.feature.video.ui

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import dev.terashima.yomitorirss.feature.video.VideoItem
import dev.terashima.yomitorirss.feature.video.VideoSource
import dev.terashima.yomitorirss.feature.video.WebVideoExtractorRule

private enum class VideoTab(val label: String) {
  ALL("すべて"),
  CONTINUE("続き"),
  COMPLETED("視聴済み"),
  SETTINGS("設定"),
}

@Composable
fun VideoScreen(
  state: VideoUiState,
  onPlay: (VideoItem) -> Unit,
  onAddWeb: (String) -> Unit,
  onRefreshSmb: () -> Unit,
  onRemove: (VideoItem) -> Unit,
  onSetCompleted: (VideoItem, Boolean) -> Unit,
  onSaveExtractorRule: (WebVideoExtractorRule) -> Unit,
  onDeleteExtractorRule: (String) -> Unit,
  onDismissMessage: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val snackbar = remember { SnackbarHostState() }
  var tabName by rememberSaveable { mutableStateOf(VideoTab.ALL.name) }
  var sourceName by rememberSaveable { mutableStateOf<String?>(null) }
  var addWebVisible by remember { mutableStateOf(false) }
  var editingRule by remember { mutableStateOf<WebVideoExtractorRule?>(null) }
  var newRuleVisible by remember { mutableStateOf(false) }
  val tab = VideoTab.valueOf(tabName)
  val source = sourceName?.let { selected -> VideoSource.entries.firstOrNull { it.name == selected } }

  LaunchedEffect(state.message) {
    val message = state.message ?: return@LaunchedEffect
    snackbar.showSnackbar(message)
    onDismissMessage()
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
          onNewRule = { newRuleVisible = true },
          onEditRule = { editingRule = it },
          onDeleteRule = onDeleteExtractorRule,
        )
      } else {
        val filtered = remember(state.items, tab, source) {
          state.items.filter { item ->
            (source == null || item.source == source) && when (tab) {
              VideoTab.ALL -> true
              VideoTab.CONTINUE -> item.playbackState?.let { it.positionMs > 0L && !it.completed } == true
              VideoTab.COMPLETED -> item.playbackState?.completed == true
              VideoTab.SETTINGS -> false
            }
          }
        }
        Column(Modifier.fillMaxSize()) {
          VideoSourceFilters(
            selected = source,
            onSelected = { sourceName = it?.name },
          )
          if (filtered.isEmpty()) {
            Text(
              when (tab) {
                VideoTab.ALL -> "動画がありません。Web URLを追加するか、設定からSMB動画を同期してください。"
                VideoTab.CONTINUE -> "再生途中の動画はありません。"
                VideoTab.COMPLETED -> "視聴済みの動画はありません。"
                VideoTab.SETTINGS -> ""
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
              items(filtered, key = VideoItem::id) { item ->
                VideoCard(
                  item = item,
                  onPlay = { onPlay(item) },
                  onRemove = { onRemove(item) },
                  onSetCompleted = { completed -> onSetCompleted(item, completed) },
                )
              }
            }
          }
        }
      }

      state.busyMessage?.let { busyMessage ->
        Card(
          modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(16.dp),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(busyMessage, style = MaterialTheme.typography.bodyMedium)
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
private fun VideoCard(
  item: VideoItem,
  onPlay: () -> Unit,
  onRemove: () -> Unit,
  onSetCompleted: (Boolean) -> Unit,
) {
  var menuExpanded by remember(item.id) { mutableStateOf(false) }
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
        val completed = item.playbackState?.completed == true
        DropdownMenuItem(
          text = { Text(if (completed) "未視聴に戻す" else "視聴済みにする") },
          onClick = {
            menuExpanded = false
            onSetCompleted(!completed)
          },
        )
        DropdownMenuItem(
          text = { Text("一覧から削除") },
          onClick = {
            menuExpanded = false
            onRemove()
          },
        )
      }
    }
    Spacer(Modifier.height(6.dp))
    Text(
      when (item.source) {
        VideoSource.SMB -> "SMB"
        VideoSource.WEB -> "Web"
        VideoSource.SERVICE -> "サービス"
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
private fun VideoSettings(
  state: VideoUiState,
  onRefreshSmb: () -> Unit,
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
    Text("SMB", style = MaterialTheme.typography.titleMedium)
    Text(
      "蔵書で設定済みのSMB接続を利用します。動画側にはパスワードを保存しません。",
      style = MaterialTheme.typography.bodySmall,
    )
    Button(onClick = onRefreshSmb, enabled = !state.busy) {
      if (state.busy) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
      } else {
        Text("SMB動画を同期")
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
