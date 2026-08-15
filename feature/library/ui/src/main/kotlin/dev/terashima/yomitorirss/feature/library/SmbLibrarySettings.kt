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
  onSync: () -> Unit,
  onSave: (SmbServerSettings, String?) -> Unit,
  onDelete: (String) -> Unit,
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
  }
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
