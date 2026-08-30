package dev.terashima.yomitorirss.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun SettingsContent(
  modifier: Modifier,
  backgroundFetchWifiOnly: Boolean,
  onBackgroundFetchWifiOnlyChange: (Boolean) -> Unit,
  integratedRefreshIntervalMinutes: Long,
  onIntegratedRefreshIntervalChange: (Long) -> Unit,
  biometricLockEnabled: Boolean,
  onBiometricLockEnabledChange: (Boolean) -> Unit,
  onOpenModels: () -> Unit,
  onOpenChatGptDebug: () -> Unit,
  onOpenAiExecutionSettings: () -> Unit,
  onOpenSummaryPrompt: () -> Unit,
  onOpenAiTaskQueue: () -> Unit,
  onOpenDriveBackup: () -> Unit,
  onExportBackup: () -> Unit,
  onImportBackup: () -> Unit,
  onOpenWebServer: () -> Unit,
) {
  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(bottom = 24.dp),
  ) {
    item { SettingsHeader("セキュリティ") }
    item {
      SettingsSwitchRow(
        icon = Icons.Default.Lock,
        title = "生体認証ロック",
        supporting = "アプリ起動時とバックグラウンドから戻ったときに認証を要求",
        checked = biometricLockEnabled,
        onCheckedChange = onBiometricLockEnabledChange,
      )
    }
    item { SettingsDivider() }

    item { SettingsHeader("バックグラウンド取得") }
    item {
      SettingsIntervalRow(
        intervalMinutes = integratedRefreshIntervalMinutes,
        onIntervalChange = onIntegratedRefreshIntervalChange,
      )
    }
    item {
      SettingsSwitchRow(
        icon = Icons.Default.Download,
        title = "Wi-Fi 接続中のみ取得",
        supporting = "RSS・メール・表紙・AIモデルのバックグラウンド取得を Wi-Fi 接続中に限定",
        checked = backgroundFetchWifiOnly,
        onCheckedChange = onBackgroundFetchWifiOnlyChange,
      )
    }
    item { SettingsDivider() }

    item { SettingsHeader("AI") }
    item {
      SettingsRow(
        icon = Icons.Default.SmartToy,
        title = "AIモデル",
        supporting = "ローカル要約・蔵書整理・チャットなどで利用する端末内モデル",
        onClick = onOpenModels,
      )
    }
    item {
      SettingsRow(
        icon = Icons.Default.Cloud,
        title = "ChatGPT / Codex",
        supporting = "ChatGPTログイン、クラウドモデル選択、接続テスト",
        onClick = onOpenChatGptDebug,
      )
    }
    item {
      SettingsRow(
        icon = Icons.Default.SmartToy,
        title = "AI実行設定",
        supporting = "記事要約などAIタスクごとにローカル／クラウドの実行先を選択",
        onClick = onOpenAiExecutionSettings,
      )
    }
    item {
      SettingsRow(
        icon = Icons.Default.List,
        title = "AIタスクキュー",
        supporting = "タスク状態の確認とローカル／クラウド実行の個別一時停止・再開",
        onClick = onOpenAiTaskQueue,
      )
    }
    item {
      SettingsRow(
        icon = Icons.Default.Edit,
        title = "要約プロンプト",
        supporting = "記事・ブックマークの要約だけに使用",
        onClick = onOpenSummaryPrompt,
      )
    }
    item { SettingsDivider() }

    item { SettingsHeader("バックアップ") }
    item { SettingsRow(Icons.Default.CloudUpload, "Google Driveバックアップ", onClick = onOpenDriveBackup) }
    item { SettingsRow(Icons.Default.Download, "ファイルへバックアップ", onClick = onExportBackup) }
    item { SettingsRow(Icons.Default.Restore, "バックアップから復元", onClick = onImportBackup) }
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
private fun SettingsIntervalRow(
  intervalMinutes: Long,
  onIntervalChange: (Long) -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }
  Box {
    ListItem(
      modifier = Modifier.clickable { expanded = true },
      headlineContent = { Text("統合ビューの更新間隔") },
      supportingContent = { Text("現在: ${integratedRefreshIntervalLabel(intervalMinutes)}") },
      leadingContent = { Icon(Icons.Default.Download, contentDescription = null) },
      trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
    )
    DropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false },
    ) {
      integratedRefreshIntervalsMinutes.forEach { interval ->
        DropdownMenuItem(
          text = { Text(integratedRefreshIntervalLabel(interval)) },
          onClick = {
            expanded = false
            onIntervalChange(interval)
          },
        )
      }
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
private fun SettingsSwitchRow(
  icon: ImageVector,
  title: String,
  supporting: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  ListItem(
    modifier = Modifier.clickable { onCheckedChange(!checked) },
    headlineContent = { Text(title) },
    supportingContent = { Text(supporting) },
    leadingContent = { Icon(icon, contentDescription = null) },
    trailingContent = { Switch(checked = checked, onCheckedChange = null) },
  )
}

@Composable
private fun SettingsDivider() { HorizontalDivider() }

private val integratedRefreshIntervalsMinutes = listOf(15L, 30L, 60L, 180L, 360L, 720L, 1440L)

private fun integratedRefreshIntervalLabel(minutes: Long): String = when {
  minutes < 60 -> "${minutes}分"
  minutes % 1440L == 0L -> "${minutes / 1440L}日"
  else -> "${minutes / 60L}時間"
}
