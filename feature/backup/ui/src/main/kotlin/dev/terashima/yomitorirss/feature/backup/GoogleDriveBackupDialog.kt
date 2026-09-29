package dev.terashima.yomitorirss.feature.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun GoogleDriveBackupDialog(
  state: BackupUiState,
  onDismiss: () -> Unit,
  onSelectFolder: () -> Unit,
  onBackupNow: () -> Unit,
  onWifiOnlyChange: (Boolean) -> Unit,
  onAddScheduleTime: (Int, Int) -> Unit,
  onRemoveScheduleTime: (BackupScheduleTime) -> Unit,
  onDisable: () -> Unit,
) {
  var showTimePicker by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("バックアップ") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text("保存先と自動バックアップ時刻を設定します。指定時刻は端末のバックグラウンド実行制約により遅れる場合があります。")
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
          Text(if (state.configured) "保存先を変更" else "バックアップ先のフォルダを選択")
        }
        if (state.configured) {
          HorizontalDivider()
          Text("自動バックアップ時刻", style = MaterialTheme.typography.titleSmall)
          if (state.scheduleTimes.isEmpty()) {
            Text(
              "時刻が登録されていないため、自動バックアップは実行されません。",
              style = MaterialTheme.typography.bodySmall,
            )
          } else {
            state.scheduleTimes.forEach { time ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(time.encoded)
                TextButton(
                  onClick = { onRemoveScheduleTime(time) },
                  enabled = !state.running,
                ) {
                  Text("削除")
                }
              }
            }
          }
          OutlinedButton(
            onClick = { showTimePicker = true },
            enabled = !state.running,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("バックアップ時刻を追加")
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Wi-Fi接続時のみバックアップ")
              Text(
                "自動・手動バックアップをWi-Fi接続中だけ実行します",
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
            Text("バックアップ先設定を解除")
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text("閉じる") }
    },
  )

  if (showTimePicker) {
    BackupTimePickerDialog(
      onDismiss = { showTimePicker = false },
      onConfirm = { hour, minute ->
        onAddScheduleTime(hour, minute)
        showTimePicker = false
      },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupTimePickerDialog(
  onDismiss: () -> Unit,
  onConfirm: (Int, Int) -> Unit,
) {
  val now = remember { LocalTime.now() }
  val state = rememberTimePickerState(
    initialHour = now.hour,
    initialMinute = now.minute,
    is24Hour = true,
  )
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("バックアップ時刻") },
    text = { TimePicker(state = state) },
    confirmButton = {
      TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
        Text("追加")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("キャンセル")
      }
    },
  )
}

private fun formatBackupTime(value: String): String = runCatching {
  DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
    .withZone(ZoneId.systemDefault())
    .format(Instant.parse(value))
}.getOrDefault(value)
