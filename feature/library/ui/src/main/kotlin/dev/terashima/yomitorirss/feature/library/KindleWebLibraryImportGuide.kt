package dev.terashima.yomitorirss.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
internal fun KindleWebLibraryImportGuide() {
  val importJson = LocalWebLibraryImportHandler.current
  var showWebImport by rememberSaveable { mutableStateOf(false) }
  var showPersonalDocumentWebImport by rememberSaveable { mutableStateOf(false) }

  if (showWebImport) {
    AmazonWebLibraryImportDialog(
      source = LibrarySource.KINDLE,
      onDismiss = { showWebImport = false },
      onImportJson = importJson,
    )
  }

  if (showPersonalDocumentWebImport) {
    AmazonWebLibraryImportDialog(
      source = LibrarySource.KINDLE,
      onDismiss = { showPersonalDocumentWebImport = false },
      onImportJson = importJson,
      kindlePersonalDocuments = true,
    )
  }

  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    WebLibrarySettingsFromBinding()
    HorizontalDivider()

    SmbLibrarySettingsFromBinding()
    HorizontalDivider()

    Text("Kindle インポート", style = MaterialTheme.typography.titleMedium)
    Text(
      "購入済みの Kindle 本はアプリ内の専用 WebView から取り込めます。Amazon の認証情報はインポート処理へ渡しません。",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(
      modifier = Modifier.fillMaxWidth(),
      onClick = { showWebImport = true },
    ) {
      Text("アプリ内で Kindle 本を取り込む")
    }

    HorizontalDivider()
    Text("Personal Document", style = MaterialTheme.typography.titleSmall)
    Text(
      "Send to Kindle などで追加した Personal Document もアプリ内の専用 WebView から取り込めます。ログイン後に「コンテンツと端末の管理」の Personal Document 一覧を開き、アプリが全件を取得してそのままインポートします。",
      style = MaterialTheme.typography.bodySmall,
    )
    Button(
      modifier = Modifier.fillMaxWidth(),
      onClick = { showPersonalDocumentWebImport = true },
    ) {
      Text("アプリ内で Personal Document を取り込む")
    }
    Text(
      "通常本と Personal Document は別々に再インポートでき、片方を更新してももう片方は残ります。Personal Document をタップすると Kindle アプリを起動し、タイトルをクリップボードへコピーします。",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Text(
      "認証状態は専用 WebView プロファイル内だけに保持します。collector はログイン済み Amazon ページ内で動作し、Cookie、CSRF token、端末情報をインポート JSON へ含めません。",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
