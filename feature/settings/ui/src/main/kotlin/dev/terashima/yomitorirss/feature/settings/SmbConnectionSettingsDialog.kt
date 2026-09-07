package dev.terashima.yomitorirss.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.terashima.yomitorirss.feature.library.SmbConnectionProfile
import dev.terashima.yomitorirss.feature.library.SmbConnectionProfileRepository
import kotlinx.coroutines.launch

@Composable
internal fun SmbConnectionSettingsDialog(
  repository: SmbConnectionProfileRepository,
  onDismiss: () -> Unit,
) {
  val scope = rememberCoroutineScope()
  var profiles by remember { mutableStateOf<List<SmbConnectionProfile>>(emptyList()) }
  var loading by remember { mutableStateOf(true) }
  var busy by remember { mutableStateOf(false) }
  var message by remember { mutableStateOf<String?>(null) }
  var editing by remember { mutableStateOf<SmbConnectionProfile?>(null) }
  var creating by remember { mutableStateOf(false) }
  var deleting by remember { mutableStateOf<SmbConnectionProfile?>(null) }

  fun reload() {
    scope.launch {
      runCatching { repository.connectionProfiles() }.fold(
        onSuccess = {
          profiles = it
          loading = false
          busy = false
        },
        onFailure = {
          loading = false
          busy = false
          message = it.message ?: "SMB接続設定を読み込めませんでした"
        },
      )
    }
  }

  LaunchedEffect(Unit) { reload() }

  if (creating || editing != null) {
    SmbConnectionProfileEditDialog(
      profile = editing,
      busy = busy,
      onDismiss = {
        creating = false
        editing = null
      },
      onSave = { profile, password ->
        busy = true
        message = null
        scope.launch {
          runCatching { repository.saveConnectionProfile(profile, password) }.fold(
            onSuccess = {
              creating = false
              editing = null
              reload()
            },
            onFailure = {
              busy = false
              message = it.message ?: "SMB接続設定を保存できませんでした"
            },
          )
        }
      },
    )
  }

  deleting?.let { profile ->
    AlertDialog(
      onDismissRequest = { if (!busy) deleting = null },
      title = { Text("SMB接続を削除") },
      text = {
        Text("「${profile.name}」の接続設定と保存済みパスワードを削除します。蔵書の同期場所は解除され、動画側の同期場所は無効な参照として残ります。")
      },
      confirmButton = {
        TextButton(
          enabled = !busy,
          onClick = {
            busy = true
            scope.launch {
              runCatching { repository.deleteConnectionProfile(profile.id) }.fold(
                onSuccess = {
                  deleting = null
                  reload()
                },
                onFailure = {
                  busy = false
                  message = it.message ?: "SMB接続設定を削除できませんでした"
                },
              )
            }
          },
        ) { Text("削除") }
      },
      dismissButton = {
        TextButton(enabled = !busy, onClick = { deleting = null }) { Text("キャンセル") }
      },
    )
  }

  AlertDialog(
    onDismissRequest = { if (!busy) onDismiss() },
    title = { Text("SMB接続") },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 560.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          "蔵書と動画で共通利用する接続先と認証情報を管理します。shareとパスは各機能の設定で指定します。",
          style = MaterialTheme.typography.bodySmall,
        )
        message?.let {
          Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (loading) {
          CircularProgressIndicator()
        } else if (profiles.isEmpty()) {
          Text("SMB接続は未登録です。", style = MaterialTheme.typography.bodyMedium)
        } else {
          profiles.forEach { profile ->
            Card(Modifier.fillMaxWidth()) {
              Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Text(profile.name, style = MaterialTheme.typography.titleSmall)
                Text(
                  "${profile.host}:${profile.port} / ${profile.username}" +
                    profile.domain.takeIf(String::isNotBlank)?.let { "@$it" }.orEmpty(),
                  style = MaterialTheme.typography.bodySmall,
                )
                Text(
                  if (profile.credentialConfigured) "パスワード: 保存済み" else "パスワード: 未設定",
                  style = MaterialTheme.typography.labelSmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  TextButton(enabled = !busy, onClick = { editing = profile }) { Text("編集") }
                  TextButton(enabled = !busy, onClick = { deleting = profile }) { Text("削除") }
                }
              }
            }
          }
        }
        TextButton(enabled = !busy, onClick = { creating = true }) { Text("接続を追加") }
        Text(
          "パスワードは画面に再表示せず、Android Keystoreの鍵で暗号化して端末内に保存します。既存設定の編集ではパスワードを空欄にすると現在値を維持します。",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
    confirmButton = {
      TextButton(enabled = !busy, onClick = onDismiss) { Text("閉じる") }
    },
  )
}

@Composable
private fun SmbConnectionProfileEditDialog(
  profile: SmbConnectionProfile?,
  busy: Boolean,
  onDismiss: () -> Unit,
  onSave: (SmbConnectionProfile, String?) -> Unit,
) {
  var name by remember(profile?.id) { mutableStateOf(profile?.name.orEmpty()) }
  var host by remember(profile?.id) { mutableStateOf(profile?.host.orEmpty()) }
  var port by remember(profile?.id) { mutableStateOf((profile?.port ?: 445).toString()) }
  var username by remember(profile?.id) { mutableStateOf(profile?.username.orEmpty()) }
  var domain by remember(profile?.id) { mutableStateOf(profile?.domain.orEmpty()) }
  var password by remember(profile?.id) { mutableStateOf("") }
  val parsedPort = port.toIntOrNull()
  val valid = name.isNotBlank() && host.isNotBlank() && username.isNotBlank() &&
    parsedPort != null && parsedPort in 1..65535 &&
    (profile?.credentialConfigured == true || password.isNotEmpty())

  AlertDialog(
    onDismissRequest = { if (!busy) onDismiss() },
    title = { Text(if (profile == null) "SMB接続を追加" else "SMB接続を編集") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(name, { name = it }, label = { Text("表示名") }, singleLine = true)
        OutlinedTextField(host, { host = it }, label = { Text("ホスト") }, singleLine = true)
        OutlinedTextField(
          port,
          { port = it.filter(Char::isDigit) },
          label = { Text("ポート") },
          singleLine = true,
        )
        OutlinedTextField(username, { username = it }, label = { Text("ユーザー名") }, singleLine = true)
        OutlinedTextField(domain, { domain = it }, label = { Text("ドメイン（任意）") }, singleLine = true)
        OutlinedTextField(
          value = password,
          onValueChange = { password = it },
          label = { Text(if (profile == null) "パスワード" else "パスワード（変更時のみ）") },
          visualTransformation = PasswordVisualTransformation(),
          singleLine = true,
        )
      }
    },
    confirmButton = {
      TextButton(
        enabled = valid && !busy,
        onClick = {
          onSave(
            SmbConnectionProfile(
              id = profile?.id.orEmpty(),
              name = name.trim(),
              host = host.trim(),
              port = requireNotNull(parsedPort),
              username = username.trim(),
              domain = domain.trim(),
              credentialConfigured = profile?.credentialConfigured == true,
            ),
            password.takeIf(String::isNotEmpty),
          )
        },
      ) { Text("保存") }
    },
    dismissButton = {
      TextButton(enabled = !busy, onClick = onDismiss) { Text("キャンセル") }
    },
  )
}
