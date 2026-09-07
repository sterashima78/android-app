package dev.terashima.yomitorirss.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
internal fun SmbLibraryLocationSettingsSection(
  repository: SmbConnectionProfileRepository,
  busy: Boolean,
  syncing: Boolean,
  coverPrefetchBusy: Boolean,
  coverPrefetch: SmbCoverPrefetchSnapshot,
  onSync: () -> Unit,
  onEnqueueCovers: () -> Unit,
  onRetryFailedCovers: () -> Unit,
  onRescheduleCovers: () -> Unit,
) {
  val scope = rememberCoroutineScope()
  var profiles by remember { mutableStateOf<List<SmbConnectionProfile>>(emptyList()) }
  var locations by remember { mutableStateOf<List<SmbLibraryLocation>>(emptyList()) }
  var loading by remember { mutableStateOf(true) }
  var localBusy by remember { mutableStateOf(false) }
  var message by remember { mutableStateOf<String?>(null) }
  var editingProfile by remember { mutableStateOf<SmbConnectionProfile?>(null) }
  var deletingLocation by remember { mutableStateOf<SmbLibraryLocation?>(null) }

  fun reload() {
    scope.launch {
      runCatching {
        repository.connectionProfiles() to repository.libraryLocations()
      }.fold(
        onSuccess = { (loadedProfiles, loadedLocations) ->
          profiles = loadedProfiles
          locations = loadedLocations
          loading = false
          localBusy = false
        },
        onFailure = {
          loading = false
          localBusy = false
          message = it.message ?: "SMB設定を読み込めませんでした"
        },
      )
    }
  }

  LaunchedEffect(Unit) { reload() }

  editingProfile?.let { profile ->
    val location = locations.firstOrNull { it.serverId == profile.id }
    SmbLibraryLocationDialog(
      profile = profile,
      location = location,
      busy = busy || syncing || localBusy,
      onDismiss = { editingProfile = null },
      onSave = { saved ->
        localBusy = true
        scope.launch {
          runCatching { repository.saveLibraryLocation(saved) }.fold(
            onSuccess = {
              editingProfile = null
              reload()
            },
            onFailure = {
              localBusy = false
              message = it.message ?: "蔵書のSMB同期場所を保存できませんでした"
            },
          )
        }
      },
    )
  }

  deletingLocation?.let { location ->
    val profileName = profiles.firstOrNull { it.id == location.serverId }?.name ?: "この接続先"
    AlertDialog(
      onDismissRequest = { if (!localBusy) deletingLocation = null },
      title = { Text("蔵書の同期場所を解除") },
      text = { Text("「$profileName」のshare/pathを蔵書の同期対象から外します。全体設定のSMB接続とパスワードは削除しません。") },
      confirmButton = {
        TextButton(
          enabled = !localBusy && !busy && !syncing,
          onClick = {
            localBusy = true
            scope.launch {
              runCatching { repository.deleteLibraryLocation(location.serverId) }.fold(
                onSuccess = {
                  deletingLocation = null
                  reload()
                },
                onFailure = {
                  localBusy = false
                  message = it.message ?: "蔵書のSMB同期場所を解除できませんでした"
                },
              )
            }
          },
        ) { Text("解除") }
      },
      dismissButton = { TextButton(onClick = { deletingLocation = null }) { Text("キャンセル") } },
    )
  }

  Column(
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text("ファイルサーバ", style = MaterialTheme.typography.titleMedium)
    Text(
      "全体設定のSMB接続を利用し、蔵書として同期するshareとパスだけをここで指定します。",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    message?.let {
      Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }

    if (loading) {
      CircularProgressIndicator()
    } else if (profiles.isEmpty()) {
      Text(
        "SMB接続がありません。アプリの全体設定からSMB接続を追加してください。",
        style = MaterialTheme.typography.bodyMedium,
      )
    } else {
      profiles.forEach { profile ->
        val location = locations.firstOrNull { it.serverId == profile.id }
        Card(Modifier.fillMaxWidth()) {
          Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(profile.name, style = MaterialTheme.typography.titleSmall)
            Text(
              "${profile.host}:${profile.port}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              location?.let {
                it.share + it.rootPath.takeIf(String::isNotBlank)?.let { path -> " / $path" }.orEmpty()
              } ?: "蔵書の同期場所は未設定",
              style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              TextButton(
                enabled = !busy && !syncing && !localBusy,
                onClick = { editingProfile = profile },
              ) { Text(if (location == null) "同期場所を設定" else "編集") }
              if (location != null) {
                TextButton(
                  enabled = !busy && !syncing && !localBusy,
                  onClick = { deletingLocation = location },
                ) { Text("解除") }
              }
            }
          }
        }
      }
    }

    Button(
      enabled = locations.isNotEmpty() && !busy && !syncing && !localBusy,
      onClick = onSync,
    ) {
      if (syncing) CircularProgressIndicator(strokeWidth = 2.dp) else Text("ファイルサーバを同期")
    }

    HorizontalDivider()
    SmbCoverPrefetchQueuePreservedSection(
      snapshot = coverPrefetch,
      busy = coverPrefetchBusy,
      enabled = locations.isNotEmpty() && !syncing,
      onEnqueue = onEnqueueCovers,
      onRetryFailed = onRetryFailedCovers,
      onReschedule = onRescheduleCovers,
    )
  }
}

@Composable
private fun SmbCoverPrefetchQueuePreservedSection(
  snapshot: SmbCoverPrefetchSnapshot,
  busy: Boolean,
  enabled: Boolean,
  onEnqueue: () -> Unit,
  onRetryFailed: () -> Unit,
  onReschedule: () -> Unit,
) {
  val canReschedule = snapshot.pendingCount > 0 &&
    snapshot.runtime.state == SmbCoverPrefetchWorkerState.ENQUEUED &&
    snapshot.runtime.waitReason == SmbCoverPrefetchWaitReason.SCHEDULER
  val visibleItems = visibleSmbCoverPrefetchItems(snapshot.items)

  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text("表紙先読みキュー", style = MaterialTheme.typography.titleMedium)
    Text(
      "未取得の表紙をバックグラウンドで取得します。処理はWi-Fi接続時かつバッテリー低下中でない場合に実行し、Wi-Fiが従量制設定でも停止しません。ZIP / CBZ は先頭128MiBまでを走査し、PDFは512MiB以下だけ一時取得して1ページ目を表紙化し、本体は処理後に削除します。対象外になった場合は理由とファイルサイズを表示します。",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      "実行中 ${snapshot.runningCount} ・ 待機 ${snapshot.pendingCount} ・ 完了 ${snapshot.completedCount} ・ 失敗 ${snapshot.failedCount} ・ 対象外 ${snapshot.skippedCount}",
      style = MaterialTheme.typography.bodySmall,
    )
    if (snapshot.hasActiveWork || snapshot.runtime.state != SmbCoverPrefetchWorkerState.IDLE) {
      Text(
        "WorkManager: ${coverPrefetchWorkerStatePreservedLabel(snapshot.runtime.state)}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      snapshot.runtime.waitReason?.let { reason ->
        Text(
          coverPrefetchWaitReasonPreservedLabel(reason),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (canReschedule) {
        Button(
          enabled = enabled && !busy,
          onClick = onReschedule,
        ) {
          Text("実行を再要求")
        }
      }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(
        enabled = enabled && !busy,
        onClick = onEnqueue,
      ) {
        if (busy) {
          CircularProgressIndicator(strokeWidth = 2.dp)
        } else {
          Text("未取得表紙を先読み")
        }
      }
      if (snapshot.failedCount > 0) {
        TextButton(
          enabled = enabled && !busy,
          onClick = onRetryFailed,
        ) { Text("失敗を再試行") }
      }
    }

    if (visibleItems.isEmpty()) {
      Text(
        "表示する表紙先読みジョブはありません。ファイルサーバ同期時にも未取得分が自動でキューへ追加されます。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      visibleItems.take(MAX_VISIBLE_SMB_COVER_QUEUE_ROWS).forEach { item ->
        SmbCoverPrefetchPreservedRow(item)
      }
      if (visibleItems.size > MAX_VISIBLE_SMB_COVER_QUEUE_ROWS) {
        Text(
          "最新 $MAX_VISIBLE_SMB_COVER_QUEUE_ROWS 件を表示しています。",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun SmbCoverPrefetchPreservedRow(item: SmbCoverPrefetchItem) {
  Card(Modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(
        item.title,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        coverPrefetchStatusPreservedLabel(item.status),
        style = MaterialTheme.typography.labelSmall,
        color = if (item.status == SmbCoverPrefetchStatus.FAILED) {
          MaterialTheme.colorScheme.error
        } else {
          MaterialTheme.colorScheme.onSurfaceVariant
        },
      )
      if (item.status == SmbCoverPrefetchStatus.RUNNING) {
        if (item.totalBytes > 0L) {
          val fraction = (item.downloadedBytes.toDouble() / item.totalBytes.toDouble())
            .coerceIn(0.0, 1.0)
            .toFloat()
          Text(
            "${formatPreservedBytes(item.downloadedBytes)} / ${formatPreservedBytes(item.totalBytes)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
          )
        } else {
          LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
      }
      item.message?.takeIf(String::isNotBlank)?.let { message ->
        Text(
          message,
          style = MaterialTheme.typography.labelSmall,
          color = if (item.status == SmbCoverPrefetchStatus.FAILED) {
            MaterialTheme.colorScheme.error
          } else {
            MaterialTheme.colorScheme.onSurfaceVariant
          },
        )
      }
    }
  }
}

private fun coverPrefetchStatusPreservedLabel(status: SmbCoverPrefetchStatus): String = when (status) {
  SmbCoverPrefetchStatus.PENDING -> "待機中"
  SmbCoverPrefetchStatus.RUNNING -> "取得中"
  SmbCoverPrefetchStatus.FAILED -> "失敗"
  SmbCoverPrefetchStatus.COMPLETED -> "完了"
  SmbCoverPrefetchStatus.SKIPPED -> "対象外"
}

private fun coverPrefetchWorkerStatePreservedLabel(state: SmbCoverPrefetchWorkerState): String = when (state) {
  SmbCoverPrefetchWorkerState.IDLE -> "未実行"
  SmbCoverPrefetchWorkerState.ENQUEUED -> "実行待ち"
  SmbCoverPrefetchWorkerState.RUNNING -> "実行中"
  SmbCoverPrefetchWorkerState.BLOCKED -> "前段ジョブ待ち"
  SmbCoverPrefetchWorkerState.FAILED -> "失敗"
  SmbCoverPrefetchWorkerState.CANCELLED -> "キャンセル済み"
  SmbCoverPrefetchWorkerState.UNKNOWN -> "状態を取得できません"
}

private fun coverPrefetchWaitReasonPreservedLabel(reason: SmbCoverPrefetchWaitReason): String = when (reason) {
  SmbCoverPrefetchWaitReason.WIFI -> "待機理由: Wi-Fi接続を待っています。"
  SmbCoverPrefetchWaitReason.BATTERY -> "待機理由: バッテリー残量が低いため待機しています。"
  SmbCoverPrefetchWaitReason.SCHEDULER ->
    "実行条件は満たしています。通常は自動的に開始しますが、OSのバックグラウンド実行制御により開始が遅れることがあります。"
}

private fun formatPreservedBytes(bytes: Long): String = when {
  bytes >= 1024L * 1024L -> String.format("%.1f MB", bytes.toDouble() / (1024.0 * 1024.0))
  bytes >= 1024L -> String.format("%.1f KB", bytes.toDouble() / 1024.0)
  else -> "$bytes B"
}

@Composable
private fun SmbLibraryLocationDialog(
  profile: SmbConnectionProfile,
  location: SmbLibraryLocation?,
  busy: Boolean,
  onDismiss: () -> Unit,
  onSave: (SmbLibraryLocation) -> Unit,
) {
  var share by remember(profile.id, location) { mutableStateOf(location?.share.orEmpty()) }
  var rootPath by remember(profile.id, location) { mutableStateOf(location?.rootPath.orEmpty()) }
  AlertDialog(
    onDismissRequest = { if (!busy) onDismiss() },
    title = { Text("${profile.name} の蔵書同期場所") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = share,
          onValueChange = { share = it },
          label = { Text("share") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = rootPath,
          onValueChange = { rootPath = it },
          label = { Text("パス（任意）") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Text(
          "例: share = media、パス = books\\comics。パスを空欄にするとshare直下から同期します。",
          style = MaterialTheme.typography.labelSmall,
        )
      }
    },
    confirmButton = {
      TextButton(
        enabled = share.isNotBlank() && !busy,
        onClick = {
          onSave(
            SmbLibraryLocation(
              serverId = profile.id,
              share = share,
              rootPath = rootPath,
            ),
          )
        },
      ) { Text("保存") }
    },
    dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("キャンセル") } },
  )
}

private const val MAX_VISIBLE_SMB_COVER_QUEUE_ROWS = 30
