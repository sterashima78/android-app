package dev.terashima.yomitorirss.feature.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun GoogleDriveBackupDialog(
  state: BackupUiState,
  onDismiss: () -> Unit,
  onSelectFolder: () -> Unit,
  onBackupNow: () -> Unit,
  onWifiOnlyChange: (Boolean) -> Unit,
  onDisable: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Google Driveバックアップ") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Google Drive上のフォルダを選択すると、変更から15分後と1日1回、バックアップを保存します。最新10世代を保持します。")
        HorizontalDivider()
        Text(
          if (state.configured) "保存先: ${state.folderName ?: "選択済みフォルダ"}" else "保存先は未設定です",
          style = MaterialTheme.typography.bodyMedium,
        )
        state.lastSuccessAt?.let {
          Text("最終成功: ${formatBackupTime(it)}", style = MaterialTheme.typography.bodySmall)
        }
        state.lastFileName?.let {
          Text("最終ファイル: $it", style = MaterialTheme.typography.bodySmall)
        }
        state.lastError?.let {
          Text(
            "直近のエラー: $it",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
          )
        }
        if (state.running) {
          CircularProgressIndicator()
        }
        Button(
          onClick = onSelectFolder,
          enabled = !state.running,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(if (state.configured) "保存先を変更" else "Google Driveのフォルダを選択")
        }
        if (state.configured) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Wi-Fi接続時のみバックアップ")
              Text(
                "Google DriveへのバックアップをWi-Fi接続中だけ実行します",
                style = MaterialTheme.typography.bodySmall,
              )
            }
            Switch(
              checked = state.wifiOnly,
              onCheckedChange = onWifiOnlyChange,
              enabled = !state.running,
            )
          }
          OutlinedButton(
            onClick = onBackupNow,
            enabled = !state.running,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("今すぐバックアップ")
          }
          TextButton(
            onClick = onDisable,
            enabled = !state.running,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("自動バックアップを無効にする")
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text("閉じる") }
    },
  )
}

private fun formatBackupTime(value: String): String = runCatching {
  DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
    .withZone(ZoneId.systemDefault())
    .format(Instant.parse(value))
}.getOrDefault(value)
