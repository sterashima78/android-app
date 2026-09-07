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
    Text("表紙先読みキュー", style = MaterialTheme.typography.titleMedium)
    Text(
      "実行中 ${coverPrefetch.runningCount} ・ 待機 ${coverPrefetch.pendingCount} ・ 完了 ${coverPrefetch.completedCount} ・ 失敗 ${coverPrefetch.failedCount} ・ 対象外 ${coverPrefetch.skippedCount}",
      style = MaterialTheme.typography.bodySmall,
    )
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Button(
        enabled = locations.isNotEmpty() && !coverPrefetchBusy && !syncing,
        onClick = onEnqueueCovers,
      ) { Text("未取得表紙を先読み") }
      if (coverPrefetch.failedCount > 0) {
        TextButton(
          enabled = !coverPrefetchBusy && !syncing,
          onClick = onRetryFailedCovers,
        ) { Text("失敗を再試行") }
      }
    }
    if (
      coverPrefetch.pendingCount > 0 &&
      coverPrefetch.runtime.state == SmbCoverPrefetchWorkerState.ENQUEUED &&
      coverPrefetch.runtime.waitReason == SmbCoverPrefetchWaitReason.SCHEDULER
    ) {
      TextButton(
        enabled = !coverPrefetchBusy && !syncing,
        onClick = onRescheduleCovers,
      ) { Text("実行を再要求") }
    }
  }
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
