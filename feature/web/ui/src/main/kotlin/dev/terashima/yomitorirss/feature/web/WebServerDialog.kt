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

@Composable
fun WebServerDialog(
  state: LanWebServerState,
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
            Text(
              "この初回URLはブラウザ履歴に残る場合がありますが、認証時に一度だけ使用され、以後はトークンを含まないURLへ移動します。共有しないでください。",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Text(
            "通信は暗号化されないHTTPです。盗聴のおそれがあるため、自宅など信頼できるLANでのみ使用してください。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
          Text(
            "アプリを閉じてもサーバは動作します。通知の本体または停止ボタンをタップすると停止します。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          Text("未読記事、ブックマーク、あとで読む記事、フィード一覧を同一LAN内から閲覧できます。")
          Text(
            "起動すると個人データを暗号化されていないHTTPで公開します。信頼できるLANに接続している場合に限り開始してください。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
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
          Button(onClick = onStart) { Text("理解して開始") }
        }
      }
    },
    dismissButton = {
      OutlinedButton(onClick = onDismiss) { Text("閉じる") }
    },
  )
}

internal fun webServerStatusText(state: LanWebServerState): String =
  if (state.running) "起動中" else "停止中"
