package dev.terashima.yomitorirss.feature.rss

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun FeedScreen(
  modifier: Modifier,
  feeds: List<Feed>,
  folders: List<FeedFolder>,
  onAdd: () -> Unit,
  onDelete: (Feed) -> Unit,
  onCreateFolder: (String) -> Unit,
  onRenameFolder: (FeedFolder, String) -> Unit,
  onDeleteFolder: (FeedFolder) -> Unit,
  onMoveFeed: (Feed, String?) -> Unit,
) {
  var creatingFolder by remember { mutableStateOf(false) }
  var renamingFolder by remember { mutableStateOf<FeedFolder?>(null) }
  var deletingFolder by remember { mutableStateOf<FeedFolder?>(null) }
  var movingFeed by remember { mutableStateOf<Feed?>(null) }
  val uncategorized = feeds.filter { it.folderId == null || folders.none { folder -> folder.id == it.folderId } }

  LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
    item("actions") {
      Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedButton(onClick = { creatingFolder = true }, modifier = Modifier.weight(1f)) {
          Icon(Icons.Default.CreateNewFolder, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text("フォルダ作成")
        }
        FilledTonalButton(onClick = onAdd, modifier = Modifier.weight(1f)) {
          Icon(Icons.Default.Add, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text("フィード追加")
        }
      }
    }

    if (feeds.isEmpty() && folders.isEmpty()) {
      item("empty") {
        Column(
          Modifier.fillParentMaxSize(),
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text("フィードが登録されていません")
          Spacer(Modifier.height(8.dp))
          Text(
            "フィードを追加するか、先にフォルダを作成できます",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    if (uncategorized.isNotEmpty()) {
      item("uncategorized-header") {
        FeedFolderHeader(title = "未分類", count = uncategorized.size)
      }
      items(uncategorized, key = Feed::id) { feed ->
        FeedCard(feed = feed, onMove = { movingFeed = feed }, onDelete = onDelete)
      }
    }

    folders.forEach { folder ->
      val folderFeeds = feeds.filter { it.folderId == folder.id }
      item("folder-${folder.id}") {
        FeedFolderHeader(
          title = folder.name,
          count = folderFeeds.size,
          onRename = { renamingFolder = folder },
          onDelete = { deletingFolder = folder },
        )
      }
      if (folderFeeds.isEmpty()) {
        item("folder-empty-${folder.id}") {
          Text(
            text = "フィードなし",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
          )
        }
      } else {
        items(folderFeeds, key = Feed::id) { feed ->
          FeedCard(feed = feed, onMove = { movingFeed = feed }, onDelete = onDelete)
        }
      }
    }
  }

  if (creatingFolder) {
    FeedFolderNameDialog(
      title = "フォルダを作成",
      confirmLabel = "作成",
      initialValue = "",
      onDismiss = { creatingFolder = false },
      onConfirm = {
        creatingFolder = false
        onCreateFolder(it)
      },
    )
  }

  renamingFolder?.let { folder ->
    FeedFolderNameDialog(
      title = "フォルダ名を変更",
      confirmLabel = "保存",
      initialValue = folder.name,
      onDismiss = { renamingFolder = null },
      onConfirm = {
        renamingFolder = null
        onRenameFolder(folder, it)
      },
    )
  }

  deletingFolder?.let { folder ->
    AlertDialog(
      onDismissRequest = { deletingFolder = null },
      title = { Text("フォルダを削除しますか？") },
      text = { Text("「${folder.name}」内のフィードは未分類へ移動します。フィード自体は削除されません。") },
      confirmButton = {
        TextButton(
          onClick = {
            deletingFolder = null
            onDeleteFolder(folder)
          },
        ) { Text("削除") }
      },
      dismissButton = { TextButton(onClick = { deletingFolder = null }) { Text("キャンセル") } },
    )
  }

  movingFeed?.let { feed ->
    FeedMoveDialog(
      feed = feed,
      folders = folders,
      onDismiss = { movingFeed = null },
      onSelect = { folderId ->
        movingFeed = null
        onMoveFeed(feed, folderId)
      },
    )
  }
}

@Composable
private fun FeedFolderHeader(
  title: String,
  count: Int,
  onRename: (() -> Unit)? = null,
  onDelete: (() -> Unit)? = null,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.width(8.dp))
    Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
    Text(
      "${count}件",
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    onRename?.let {
      IconButton(onClick = it) { Icon(Icons.Default.Edit, contentDescription = "フォルダ名を変更") }
    }
    onDelete?.let {
      IconButton(onClick = it) { Icon(Icons.Default.Delete, contentDescription = "フォルダを削除") }
    }
  }
  HorizontalDivider()
}

@Composable
private fun FeedCard(
  feed: Feed,
  onMove: () -> Unit,
  onDelete: (Feed) -> Unit,
) {
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
      IconButton(onClick = onMove) {
        Icon(Icons.Default.DriveFileMove, contentDescription = "フォルダを移動")
      }
      IconButton(onClick = { onDelete(feed) }) {
        Icon(Icons.Default.Delete, contentDescription = "削除")
      }
    }
  }
}

@Composable
private fun FeedFolderNameDialog(
  title: String,
  confirmLabel: String,
  initialValue: String,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
) {
  var value by remember(initialValue) { mutableStateOf(initialValue) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      OutlinedTextField(
        value = value,
        onValueChange = { value = it },
        label = { Text("フォルダ名") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
    },
    confirmButton = {
      TextButton(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) { Text(confirmLabel) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
  )
}

@Composable
private fun FeedMoveDialog(
  feed: Feed,
  folders: List<FeedFolder>,
  onDismiss: () -> Unit,
  onSelect: (String?) -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("フォルダへ移動") },
    text = {
      LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
        item("uncategorized") {
          MoveFolderRow(
            name = "未分類",
            selected = feed.folderId == null,
            onClick = { onSelect(null) },
          )
        }
        items(folders, key = FeedFolder::id) { folder ->
          MoveFolderRow(
            name = folder.name,
            selected = feed.folderId == folder.id,
            onClick = { onSelect(folder.id) },
          )
        }
      }
    },
    confirmButton = {},
    dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
  )
}

@Composable
private fun MoveFolderRow(name: String, selected: Boolean, onClick: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(Icons.Default.Folder, contentDescription = null)
    Spacer(Modifier.width(12.dp))
    Text(name, modifier = Modifier.weight(1f))
    if (selected) Text("選択中", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
  }
}

private fun feedTimeLabel(value: String): String = runCatching {
  Instant.parse(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M/d HH:mm"))
}.getOrDefault("")
