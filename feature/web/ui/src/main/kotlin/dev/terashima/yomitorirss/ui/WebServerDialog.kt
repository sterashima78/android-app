package dev.terashima.yomitorirss.feature.web
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.terashima.yomitorirss.feature.web.LanServerUiState

@Composable
fun WebServerDialog(
  state: LanServerUiState,
  onDismiss: () -> Unit,
  onStart: () -> Unit,
  onStop: () -> Unit,
) {
  val accessUrl = state.accessUrl
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Web サーバ") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(webServerStatusText(state), style = MaterialTheme.typography.titleMedium)
        if (state.running) {
          if (accessUrl == null) {
            Text("同じネットワークへの接続を待っています。")
          } else {
            Text("同じネットワークのブラウザで次のURLを開いてください。")
            SelectionContainer {
              Text(accessUrl, style = MaterialTheme.typography.bodyMedium)
            }
          }
          Text(
            "アプリを閉じてもサーバは動作します。通知の本体または停止ボタンをタップすると停止します。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          Text("未読記事、ブックマーク、あとで読む記事、フィード一覧を同一LAN内から閲覧できます。")
        }
        state.error?.let {
          Text(it, color = MaterialTheme.colorScheme.error)
        }
      }
    },
    confirmButton = {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        if (state.running) {
          Button(onClick = onStop) { Text("停止") }
        } else {
          Button(onClick = onStart) { Text("開始") }
        }
      }
    },
    dismissButton = {
      OutlinedButton(onClick = onDismiss) { Text("閉じる") }
    },
  )
}

internal fun webServerStatusText(state: LanServerUiState): String =
  if (state.running) "起動中" else "停止中"
