package dev.terashima.yomitorirss.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun SettingsContent(
  modifier: Modifier,
  tagCount: Int,
  onImportBookmarkCsv: () -> Unit,
  onImportBookmarkHtml: () -> Unit,
  onOpenXCss: () -> Unit,
  onOpenModels: () -> Unit,
  onOpenSummaryPrompt: () -> Unit,
  onOpenSummaryTaskQueue: () -> Unit,
  onOpenDriveBackup: () -> Unit,
  onExportBackup: () -> Unit,
  onImportBackup: () -> Unit,
  onOpenWebServer: () -> Unit,
) {
  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(bottom = 24.dp),
  ) {
    item { SettingsHeader("ブックマーク") }
    item {
      SettingsRow(
        icon = Icons.Default.UploadFile,
        title = "CSVからインポート",
        supporting = "登録済みタグ ${tagCount}件",
        onClick = onImportBookmarkCsv,
      )
    }
    item {
      SettingsRow(
        icon = Icons.Default.UploadFile,
        title = "HTMLからインポート",
        onClick = onImportBookmarkHtml,
      )
    }
    item { SettingsDivider() }

    item { SettingsHeader("X") }
    item {
      SettingsRow(
        icon = Icons.Default.Edit,
        title = "カスタム CSS",
        supporting = "CSS の有効化・編集・デフォルト復元",
        onClick = onOpenXCss,
      )
    }
    item { SettingsDivider() }

    item { SettingsHeader("要約") }
    item {
      SettingsRow(
        icon = Icons.Default.SmartToy,
        title = "要約モデル",
        onClick = onOpenModels,
      )
    }
    item {
      SettingsRow(
        icon = Icons.Default.Edit,
        title = "要約プロンプト",
        onClick = onOpenSummaryPrompt,
      )
    }
    item {
      SettingsRow(
        icon = Icons.Default.List,
        title = "タスクキュー",
        supporting = "要約タスクの状態確認・停止・キャンセル",
        onClick = onOpenSummaryTaskQueue,
      )
    }
    item { SettingsDivider() }

    item { SettingsHeader("バックアップ") }
    item {
      SettingsRow(
        icon = Icons.Default.CloudUpload,
        title = "Google Driveバックアップ",
        onClick = onOpenDriveBackup,
      )
    }
    item {
      SettingsRow(
        icon = Icons.Default.Download,
        title = "ファイルへバックアップ",
        onClick = onExportBackup,
      )
    }
    item {
      SettingsRow(
        icon = Icons.Default.Restore,
        title = "バックアップから復元",
        onClick = onImportBackup,
      )
    }
    item { SettingsDivider() }

    item { SettingsHeader("共有") }
    item {
      SettingsRow(
        icon = Icons.Default.Dns,
        title = "Webサーバ",
        supporting = "同じネットワークからRSSとブックマークを閲覧",
        onClick = onOpenWebServer,
      )
    }
  }
}

@Composable
private fun SettingsHeader(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
  )
}

@Composable
private fun SettingsRow(
  icon: ImageVector,
  title: String,
  supporting: String? = null,
  onClick: () -> Unit,
) {
  ListItem(
    modifier = Modifier.clickable(onClick = onClick),
    headlineContent = { Text(title) },
    supportingContent = supporting?.let { { Text(it) } },
    leadingContent = { Icon(icon, contentDescription = null) },
    trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
  )
}

@Composable
private fun SettingsDivider() {
  HorizontalDivider()
}
