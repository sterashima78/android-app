package dev.terashima.yomitorirss.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun CrashDiagnosticsContent(
  report: String,
  onCopy: () -> Unit,
  onRetry: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(24.dp),
  ) {
    Text("起動エラーを検出しました", style = MaterialTheme.typography.headlineSmall)
    Text(
      "クラッシュを繰り返さないため通常の初期化を停止しています。下の情報を共有すると原因を特定できます。",
      modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
    )
    Button(onClick = onCopy) {
      Text("クラッシュ情報をコピー")
    }
    Button(
      onClick = onRetry,
      modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
    ) {
      Text("通常起動を再試行")
    }
    SelectionContainer {
      Text(report, style = MaterialTheme.typography.bodySmall)
    }
  }
}

internal fun Context.copyCrashReport(report: String) {
  val clipboard = getSystemService(ClipboardManager::class.java)
  clipboard.setPrimaryClip(ClipData.newPlainText("Yomitori crash report", report))
  Toast.makeText(this, "クラッシュ情報をコピーしました", Toast.LENGTH_SHORT).show()
}
