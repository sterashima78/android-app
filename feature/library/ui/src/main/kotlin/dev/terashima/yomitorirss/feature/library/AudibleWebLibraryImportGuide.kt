package dev.terashima.yomitorirss.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun AudibleWebLibraryImportGuide() {
  val importJson = LocalWebLibraryImportHandler.current
  var showWebImport by rememberSaveable { mutableStateOf(false) }

  if (showWebImport) {
    AmazonWebLibraryImportDialog(
      source = LibrarySource.AUDIBLE,
      onDismiss = { showWebImport = false },
      onImportJson = importJson,
    )
  }

  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text("Audible インポート", style = MaterialTheme.typography.titleMedium)
    Text(
      "アプリ内の専用 WebView で Audible にログインし、ASIN の収集、カタログ情報・表紙・再生時間・シリーズの取得、インポートまで連続して実行します。",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(
      modifier = Modifier.fillMaxWidth(),
      onClick = { showWebImport = true },
    ) {
      Text("アプリ内で Audible を取り込む")
    }
    Text(
      "アプリは Audible / Amazon のパスワードや Cookie を読み取りません。認証と Web 通信は専用 WebView プロファイル内で行い、生成した蔵書 JSON だけをインポート処理へ渡します。",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
