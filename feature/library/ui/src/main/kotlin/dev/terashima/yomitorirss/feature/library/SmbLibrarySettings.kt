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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun SmbLibrarySettingsSection(
  servers: List<SmbServerSettings>,
  busy: Boolean,
  syncing: Boolean,
  coverPrefetchBusy: Boolean,
  coverPrefetch: SmbCoverPrefetchSnapshot,
  onSync: () -> Unit,
  onSave: (SmbServerSettings, String?) -> Unit,
  onDelete: (String) -> Unit,
  onEnqueueCovers: () -> Unit,
  onRetryFailedCovers: () -> Unit,
  onRescheduleCovers: () -> Unit,
) {
  var editingServer by remember { mutableStateOf<SmbServerSettings?>(null) }
  var creatingServer by remember { mutableStateOf(false) }
  var deletingServer by remember { mutableStateOf<SmbServerSettings?>(null) }

  if (creatingServer || editingServer != null) {
    SmbServerDialog(
      server = editingServer,
      onDismiss = {
        creatingServer = false
        editingServer = null
      },
      onSave = { settings, password ->
        onSave(settings, password)
        creatingServer = false
        editingServer = null
      },
    )
  }

  deletingServer?.let { server ->
    AlertDialog(
      onDismissRequest = { deletingServer = null },
      title = { Text("SMB設定を削除") },
      text = { Text("「${server.name}」の接続設定と保存済み認証情報を削除します。") },
      confirmButton = {
        TextButton(
          onClick = {
            onDelete(server.id)
            deletingServer = null
          },
        ) { Text("削除") }
      },
      dismissButton = {
        TextButton(onClick = { deletingServer = null }) { Text("キャンセル") }
      },
    )
  }

  Column(
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(Modifier.weight(1f)) {
        Text("ファイルサーバ", style = MaterialTheme.typography.titleMedium)
        Text(
          "SMB上の ZIP / CBZ / PDF を蔵書として同期します。",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      TextButton(
        enabled = !busy && !syncing,
        onClick = { creatingServer = true },
      ) { Text("追加") }
    }

    if (servers.isEmpty()) {
      Text(
        "SMBサーバが未設定です。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      servers.forEach { server ->
        Card(Modifier.fillMaxWidth()) {
          Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(
              server.name,
              style = MaterialTheme.typography.titleSmall,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              "${server.host}:${server.port} / ${server.share}" +
                server.rootPath.takeIf(String::isNotBlank)?.let { " / $it" }.orEmpty(),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              if (server.credentialConfigured) "認証情報: 保存済み" else "認証情報: 未設定",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              TextButton(
                enabled = !busy && !syncing,
                onClick = { editingServer = server },
              ) { Text("編集") }
              TextButton(
                enabled = !busy && !syncing,
                onClick = { deletingServer = server },
              ) { Text("削除") }
            }
          }
        }
      }
    }

    Button(
      enabled = servers.isNotEmpty() && !busy && !syncing,
      onClick = onSync,
    ) {
      if (syncing) {
        CircularProgressIndicator(strokeWidth = 2.dp)
      } else {
        Text("ファイルサーバを同期")
      }
    }
    Text(
      "パスワードは画面に再表示せず、Android Keystore の鍵で暗号化して端末内に保存します。空欄のまま編集を保存すると既存のパスワードを維持します。",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    HorizontalDivider()
    SmbCoverPrefetchQueueSection(
      snapshot = coverPrefetch,
      busy = coverPrefetchBusy,
      enabled = servers.isNotEmpty() && !syncing,
      onEnqueue = onEnqueueCovers,
      onRetryFailed = onRetryFailedCovers,
      onReschedule = onRescheduleCovers,
    )
  }
}

@Composable
private fun SmbCoverPrefetchQueueSection(
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
        "WorkManager: ${coverPrefetchWorkerStateLabel(snapshot.runtime.state)}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      snapshot.runtime.waitReason?.let { reason ->
        Text(
          coverPrefetchWaitReasonLabel(reason),
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

    if (snapshot.items.isEmpty()) {
      Text(
        "表紙先読みジョブはありません。ファイルサーバ同期時にも未取得分が自動でキューへ追加されます。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      snapshot.items.take(MAX_VISIBLE_QUEUE_ROWS).forEach { item ->
        SmbCoverPrefetchRow(item)
      }
      if (snapshot.items.size > MAX_VISIBLE_QUEUE_ROWS) {
        Text(
          "最新 $MAX_VISIBLE_QUEUE_ROWS 件を表示しています。",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun SmbCoverPrefetchRow(item: SmbCoverPrefetchItem) {
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
        coverPrefetchStatusLabel(item.status),
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
            "${formatBytes(item.downloadedBytes)} / ${formatBytes(item.totalBytes)}",
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

private fun coverPrefetchStatusLabel(status: SmbCoverPrefetchStatus): String = when (status) {
  SmbCoverPrefetchStatus.PENDING -> "待機中"
  SmbCoverPrefetchStatus.RUNNING -> "取得中"
  SmbCoverPrefetchStatus.FAILED -> "失敗"
  SmbCoverPrefetchStatus.COMPLETED -> "完了"
  SmbCoverPrefetchStatus.SKIPPED -> "対象外"
}

private fun coverPrefetchWorkerStateLabel(state: SmbCoverPrefetchWorkerState): String = when (state) {
  SmbCoverPrefetchWorkerState.IDLE -> "未実行"
  SmbCoverPrefetchWorkerState.ENQUEUED -> "実行待ち"
  SmbCoverPrefetchWorkerState.RUNNING -> "実行中"
  SmbCoverPrefetchWorkerState.BLOCKED -> "前段ジョブ待ち"
  SmbCoverPrefetchWorkerState.FAILED -> "失敗"
  SmbCoverPrefetchWorkerState.CANCELLED -> "キャンセル済み"
  SmbCoverPrefetchWorkerState.UNKNOWN -> "状態を取得できません"
}

private fun coverPrefetchWaitReasonLabel(reason: SmbCoverPrefetchWaitReason): String = when (reason) {
  SmbCoverPrefetchWaitReason.WIFI -> "待機理由: Wi-Fi接続を待っています。"
  SmbCoverPrefetchWaitReason.BATTERY -> "待機理由: バッテリー残量が低いため待機しています。"
  SmbCoverPrefetchWaitReason.SCHEDULER ->
    "実行条件は満たしています。通常は自動的に開始しますが、OSのバックグラウンド実行制御により開始が遅れることがあります。"
}

private fun formatBytes(bytes: Long): String = when {
  bytes >= 1024L * 1024L -> String.format("%.1f MB", bytes.toDouble() / (1024.0 * 1024.0))
  bytes >= 1024L -> String.format("%.1f KB", bytes.toDouble() / 1024.0)
  else -> "$bytes B"
}

@Composable
private fun SmbServerDialog(
  server: SmbServerSettings?,
  onDismiss: () -> Unit,
  onSave: (SmbServerSettings, String?) -> Unit,
) {
  var name by remember(server) { mutableStateOf(server?.name.orEmpty()) }
  var host by remember(server) { mutableStateOf(server?.host.orEmpty()) }
  var port by remember(server) { mutableStateOf((server?.port ?: 445).toString()) }
  var share by remember(server) { mutableStateOf(server?.share.orEmpty()) }
  var rootPath by remember(server) { mutableStateOf(server?.rootPath.orEmpty()) }
  var username by remember(server) { mutableStateOf(server?.username.orEmpty()) }
  var domain by remember(server) { mutableStateOf(server?.domain.orEmpty()) }
  var password by remember(server) { mutableStateOf("") }
  val parsedPort = port.toIntOrNull()
  val valid = name.isNotBlank() && host.isNotBlank() && share.isNotBlank() &&
    username.isNotBlank() && parsedPort != null && parsedPort in 1..65535 &&
    (server != null || password.isNotEmpty())

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (server == null) "SMBサーバを追加" else "SMBサーバを編集") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("表示名") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = host,
          onValueChange = { host = it },
          label = { Text("ホスト名 / IPアドレス") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = port,
          onValueChange = { port = it.filter(Char::isDigit) },
          label = { Text("ポート") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = share,
          onValueChange = { share = it },
          label = { Text("共有名") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = rootPath,
          onValueChange = { rootPath = it },
          label = { Text("ルートパス（任意）") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = username,
          onValueChange = { username = it },
          label = { Text("ユーザー名") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = domain,
          onValueChange = { domain = it },
          label = { Text("ドメイン / Workgroup（任意）") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = password,
          onValueChange = { password = it },
          label = { Text(if (server == null) "パスワード" else "新しいパスワード（変更時のみ）") },
          singleLine = true,
          visualTransformation = PasswordVisualTransformation(),
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      TextButton(
        enabled = valid,
        onClick = {
          onSave(
            SmbServerSettings(
              id = server?.id.orEmpty(),
              name = name,
              host = host,
              port = requireNotNull(parsedPort),
              share = share,
              rootPath = rootPath,
              username = username,
              domain = domain,
              credentialConfigured = server?.credentialConfigured == true,
            ),
            password.takeIf(String::isNotEmpty),
          )
        },
      ) { Text("保存") }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("キャンセル") }
    },
  )
}

private const val MAX_VISIBLE_QUEUE_ROWS = 30
