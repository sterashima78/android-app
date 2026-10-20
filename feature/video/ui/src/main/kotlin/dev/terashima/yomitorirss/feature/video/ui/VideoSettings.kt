package dev.terashima.yomitorirss.feature.video.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.terashima.yomitorirss.feature.video.VideoFolder
import dev.terashima.yomitorirss.feature.video.WebVideoExtractorRule

@Composable
internal fun VideoSettings(
  state: VideoUiState,
  onRefreshSmb: () -> Unit,
  onNewFolder: () -> Unit,
  onEditFolder: (VideoFolder) -> Unit,
  onDeleteFolder: (String) -> Unit,
  onNewRule: () -> Unit,
  onEditRule: (WebVideoExtractorRule) -> Unit,
  onDeleteRule: (String) -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Text("整理フォルダ", style = MaterialTheme.typography.titleMedium)
    Text(
      "Web動画と明示保存した購読動画は、保存済み画面のフォルダへ整理できます。SMB動画は同期元のディレクトリ階層をそのまま辿ります。",
      style = MaterialTheme.typography.bodySmall,
    )
    Button(onClick = onNewFolder, enabled = !state.busy) {
      Text("フォルダを追加")
    }
    state.folders.forEach { folder ->
      Card(Modifier.fillMaxWidth()) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(folder.name, fontWeight = FontWeight.Medium)
          Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { onEditFolder(folder) }) { Text("名前変更") }
            TextButton(onClick = { onDeleteFolder(folder.id) }) { Text("削除") }
          }
        }
      }
    }

    Text("SMB", style = MaterialTheme.typography.titleMedium)
    Text(
      "全体設定のSMB接続を利用し、動画側で同期する共有・パスを設定します。動画側にはパスワードを保存しません。",
      style = MaterialTheme.typography.bodySmall,
    )
    Button(onClick = onRefreshSmb, enabled = !state.busy) {
      if (state.busy) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
      } else {
        Text("SMB動画の設定・同期")
      }
    }

    Text("Web抽出ルール", style = MaterialTheme.typography.titleMedium)
    Text(
      "既定ではOGPからタイトルとサムネイルを取得します。必要なサイトだけWebViewスクリプトで上書きし、再生URLも抽出できます。",
      style = MaterialTheme.typography.bodySmall,
    )
    Button(onClick = onNewRule, enabled = !state.busy) {
      Text("抽出ルールを追加")
    }
    state.extractorRules.forEach { rule ->
      Card(Modifier.fillMaxWidth()) {
        Column(
          modifier = Modifier.padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(rule.urlPattern, fontWeight = FontWeight.Medium)
          Text(
            buildList {
              if (!rule.titleExtractorCode.isNullOrBlank()) add("タイトル")
              if (!rule.thumbnailExtractorCode.isNullOrBlank()) add("サムネイル")
              if (!rule.playbackExtractorCode.isNullOrBlank()) add("再生URL")
            }.joinToString(" / "),
            style = MaterialTheme.typography.labelSmall,
          )
          if (rule.shareCookiesForPlayback) {
            Text(
              "再生時Cookie共有: 有効",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
            )
          }
          if (rule.shareReferrerPathForPlayback) {
            Text(
              "再生時Referer path共有: 有効",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
            )
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onEditRule(rule) }) { Text("編集") }
            TextButton(onClick = { onDeleteRule(rule.id) }) { Text("削除") }
          }
        }
      }
    }
  }
}

@Composable
internal fun VideoFolderDialog(
  initial: VideoFolder?,
  busy: Boolean,
  onDismiss: () -> Unit,
  onSave: (VideoFolder) -> Unit,
) {
  var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (initial == null) "フォルダを追加" else "フォルダ名を変更") },
    text = {
      OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("フォルダ名") },
        singleLine = true,
      )
    },
    confirmButton = {
      TextButton(
        onClick = {
          onSave(
            VideoFolder(
              id = initial?.id.orEmpty(),
              name = name.trim(),
              createdAtEpochMillis = initial?.createdAtEpochMillis ?: 0L,
              updatedAtEpochMillis = initial?.updatedAtEpochMillis ?: 0L,
            ),
          )
        },
        enabled = name.isNotBlank() && !busy,
      ) { Text("保存") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
  )
}

@Composable
internal fun AddWebVideoDialog(
  busy: Boolean,
  onDismiss: () -> Unit,
  onAdd: (String) -> Unit,
) {
  var url by remember { mutableStateOf("") }
  val valid = url.trim().startsWith("http://") || url.trim().startsWith("https://")
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Web動画を追加") },
    text = {
      OutlinedTextField(
        value = url,
        onValueChange = { url = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("URL") },
        singleLine = true,
      )
    },
    confirmButton = {
      TextButton(onClick = { onAdd(url.trim()) }, enabled = valid && !busy) { Text("追加") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
  )
}

@Composable
internal fun WebVideoExtractorRuleDialog(
  initial: WebVideoExtractorRule?,
  busy: Boolean,
  onDismiss: () -> Unit,
  onSave: (WebVideoExtractorRule) -> Unit,
) {
  var pattern by remember(initial?.id) { mutableStateOf(initial?.urlPattern.orEmpty()) }
  var titleCode by remember(initial?.id) { mutableStateOf(initial?.titleExtractorCode.orEmpty()) }
  var thumbnailCode by remember(initial?.id) { mutableStateOf(initial?.thumbnailExtractorCode.orEmpty()) }
  var playbackCode by remember(initial?.id) { mutableStateOf(initial?.playbackExtractorCode.orEmpty()) }
  var shareCookiesForPlayback by remember(initial?.id) {
    mutableStateOf(initial?.shareCookiesForPlayback ?: false)
  }
  var shareReferrerPathForPlayback by remember(initial?.id) {
    mutableStateOf(initial?.shareReferrerPathForPlayback ?: false)
  }
  var timeout by remember(initial?.id) { mutableStateOf((initial?.timeoutSeconds ?: 15).toString()) }
  val timeoutValue = timeout.toIntOrNull()
  val valid = pattern.startsWith("https://") &&
    timeoutValue != null && timeoutValue in 1..60 &&
    listOf(titleCode, thumbnailCode, playbackCode).any(String::isNotBlank)

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (initial == null) "抽出ルールを追加" else "抽出ルールを編集") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = pattern,
          onValueChange = { pattern = it },
          label = { Text("URLパターン") },
          placeholder = { Text("https://example.com/videos/*") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
        )
        OutlinedTextField(
          value = titleCode,
          onValueChange = { titleCode = it },
          label = { Text("タイトル抽出 JavaScript（任意）") },
          modifier = Modifier.fillMaxWidth(),
          minLines = 3,
        )
        OutlinedTextField(
          value = thumbnailCode,
          onValueChange = { thumbnailCode = it },
          label = { Text("サムネイル抽出 JavaScript（任意）") },
          modifier = Modifier.fillMaxWidth(),
          minLines = 3,
        )
        OutlinedTextField(
          value = playbackCode,
          onValueChange = { playbackCode = it },
          label = { Text("再生URL抽出 JavaScript（任意）") },
          modifier = Modifier.fillMaxWidth(),
          minLines = 3,
        )
        Text(
          "各関数は async ({ url }) => ({ ... }) の形式でPromiseを返します。",
          style = MaterialTheme.typography.labelSmall,
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Checkbox(
            checked = shareCookiesForPlayback,
            onCheckedChange = { shareCookiesForPlayback = it },
          )
          Column(modifier = Modifier.padding(start = 4.dp)) {
            Text("再生時にWebViewのCookieを共有する")
            Text(
              "Cookieが必要なstreamだけで有効にしてください。Cookie値は保存せず、再生中のHTTP requestにだけ利用します。",
              style = MaterialTheme.typography.labelSmall,
            )
          }
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Checkbox(
            checked = shareReferrerPathForPlayback,
            onCheckedChange = { shareReferrerPathForPlayback = it },
          )
          Column(modifier = Modifier.padding(start = 4.dp)) {
            Text("再生時にRefererのパスを共有する")
            Text(
              "再生URL抽出がreferrerUrlを返す場合だけ、そのパスをRefererへ含めます。queryとfragmentは送信しません。必要なstreamだけで有効にしてください。",
              style = MaterialTheme.typography.labelSmall,
            )
          }
        }
        OutlinedTextField(
          value = timeout,
          onValueChange = { timeout = it.filter(Char::isDigit) },
          label = { Text("タイムアウト秒") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
        )
      }
    },
    confirmButton = {
      TextButton(
        enabled = valid && !busy,
        onClick = {
          onSave(
            WebVideoExtractorRule(
              id = initial?.id.orEmpty(),
              urlPattern = pattern.trim(),
              titleExtractorCode = titleCode.trim().takeIf(String::isNotBlank),
              thumbnailExtractorCode = thumbnailCode.trim().takeIf(String::isNotBlank),
              playbackExtractorCode = playbackCode.trim().takeIf(String::isNotBlank),
              timeoutSeconds = requireNotNull(timeoutValue),
              updatedAtEpochMillis = initial?.updatedAtEpochMillis ?: 0L,
              shareCookiesForPlayback = shareCookiesForPlayback,
              shareReferrerPathForPlayback = shareReferrerPathForPlayback,
            ),
          )
        },
      ) { Text("保存") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
  )
}
