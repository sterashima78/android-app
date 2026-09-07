package dev.terashima.yomitorirss.feature.video.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.terashima.yomitorirss.feature.library.SmbConnectionProfile
import dev.terashima.yomitorirss.feature.video.VideoSmbSource

@Composable
internal fun VideoSmbSettingsDialog(
  state: VideoUiState,
  onSave: (VideoSmbSource) -> Unit,
  onDelete: (String) -> Unit,
  onSync: () -> Unit,
  onDismiss: () -> Unit,
) {
  var editing by remember { mutableStateOf<VideoSmbSource?>(null) }
  var creating by remember { mutableStateOf(false) }
  var deleting by remember { mutableStateOf<VideoSmbSource?>(null) }

  if (creating || editing != null) {
    VideoSmbSourceEditDialog(
      source = editing,
      profiles = state.smbProfiles,
      busy = state.busy,
      onDismiss = {
        creating = false
        editing = null
      },
      onSave = {
        creating = false
        editing = null
        onSave(it)
      },
    )
  }

  deleting?.let { source ->
    AlertDialog(
      onDismissRequest = { if (!state.busy) deleting = null },
      title = { Text("動画のSMB同期場所を削除") },
      text = { Text("このshare/pathを動画の同期対象から外します。全体設定のSMB接続とパスワードは削除しません。") },
      confirmButton = {
        TextButton(
          enabled = !state.busy,
          onClick = {
            deleting = null
            onDelete(source.id)
          },
        ) { Text("削除") }
      },
      dismissButton = { TextButton(onClick = { deleting = null }) { Text("キャンセル") } },
    )
  }

  AlertDialog(
    onDismissRequest = { if (!state.busy) onDismiss() },
    title = { Text("動画のSMB同期") },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 560.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          "全体設定のSMB接続を利用し、動画として同期するshareとパスをここで個別に指定します。蔵書とは別の場所を指定できます。",
          style = MaterialTheme.typography.bodySmall,
        )
        if (state.smbProfiles.isEmpty()) {
          Text("SMB接続がありません。先にアプリの全体設定から追加してください。")
        }
        state.smbSources.forEach { source ->
          val profile = state.smbProfiles.firstOrNull { it.id == source.serverId }
          Card(Modifier.fillMaxWidth()) {
            Column(
              modifier = Modifier.padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Text(profile?.name ?: "削除された接続先", style = MaterialTheme.typography.titleSmall)
              Text(
                source.share + source.rootPath.takeIf(String::isNotBlank)?.let { " / $it" }.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
              )
              if (profile == null) {
                Text("接続設定がないため同期対象外です", color = MaterialTheme.colorScheme.error)
              }
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                  enabled = !state.busy && profile != null,
                  onClick = { editing = source },
                ) { Text("編集") }
                TextButton(enabled = !state.busy, onClick = { deleting = source }) { Text("削除") }
              }
            }
          }
        }
        TextButton(
          enabled = !state.busy && state.smbProfiles.isNotEmpty(),
          onClick = { creating = true },
        ) { Text("同期場所を追加") }
        Button(
          enabled = !state.busy && state.smbSources.any { source ->
            state.smbProfiles.any { it.id == source.serverId }
          },
          onClick = onSync,
        ) { Text("今すぐ同期") }
      }
    },
    confirmButton = { TextButton(enabled = !state.busy, onClick = onDismiss) { Text("閉じる") } },
  )
}

@Composable
private fun VideoSmbSourceEditDialog(
  source: VideoSmbSource?,
  profiles: List<SmbConnectionProfile>,
  busy: Boolean,
  onDismiss: () -> Unit,
  onSave: (VideoSmbSource) -> Unit,
) {
  var selectedProfileId by remember(source?.id, profiles) {
    mutableStateOf(source?.serverId ?: profiles.firstOrNull()?.id.orEmpty())
  }
  var profileMenuExpanded by remember { mutableStateOf(false) }
  var share by remember(source?.id) { mutableStateOf(source?.share.orEmpty()) }
  var rootPath by remember(source?.id) { mutableStateOf(source?.rootPath.orEmpty()) }
  val selectedProfile = profiles.firstOrNull { it.id == selectedProfileId }

  AlertDialog(
    onDismissRequest = { if (!busy) onDismiss() },
    title = { Text(if (source == null) "動画の同期場所を追加" else "動画の同期場所を編集") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("接続先", style = MaterialTheme.typography.labelMedium)
        TextButton(onClick = { profileMenuExpanded = true }, enabled = !busy) {
          Text(selectedProfile?.name ?: "接続先を選択")
        }
        DropdownMenu(
          expanded = profileMenuExpanded,
          onDismissRequest = { profileMenuExpanded = false },
        ) {
          profiles.forEach { profile ->
            DropdownMenuItem(
              text = { Text(profile.name) },
              onClick = {
                selectedProfileId = profile.id
                profileMenuExpanded = false
              },
            )
          }
        }
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
      }
    },
    confirmButton = {
      TextButton(
        enabled = selectedProfile != null && share.isNotBlank() && !busy,
        onClick = {
          onSave(
            VideoSmbSource(
              id = source?.id.orEmpty(),
              serverId = requireNotNull(selectedProfile).id,
              share = share,
              rootPath = rootPath,
              updatedAtEpochMillis = source?.updatedAtEpochMillis ?: 0L,
            ),
          )
        },
      ) { Text("保存") }
    },
    dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("キャンセル") } },
  )
}
