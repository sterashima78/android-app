package dev.terashima.yomitorirss.feature.x

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun XViewerCustomizationDialog(
  repository: XViewerCssRepository,
  onDismiss: () -> Unit,
) {
  val defaultCss = remember(repository) { repository.defaultCss() }
  val savedSettings = remember(repository) { repository.load() }
  var settings by remember(savedSettings) { mutableStateOf(savedSettings) }
  var copyMessage by remember { mutableStateOf<String?>(null) }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(
      usePlatformDefaultWidth = false,
      dismissOnClickOutside = false,
    ),
  ) {
    Surface(
      modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing),
      color = MaterialTheme.colorScheme.surface,
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .imePadding()
          .padding(horizontal = 24.dp),
      ) {
        Text(
          text = "X 表示カスタマイズ",
          modifier = Modifier.padding(top = 20.dp, bottom = 12.dp),
          style = MaterialTheme.typography.headlineSmall,
        )

        Column(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(
            text = "カスタム CSS",
            style = MaterialTheme.typography.titleMedium,
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text("カスタム CSS を有効化")
            Switch(
              checked = settings.enabled,
              onCheckedChange = { settings = settings.copy(enabled = it) },
            )
          }
          Text("無効にすると CSS を一切注入しません")

          Text("CSS セット")
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            repeat(X_CSS_SET_COUNT) { index ->
              TextButton(
                onClick = {
                  settings = settings.selectSet(index)
                  copyMessage = null
                },
              ) {
                val selectedMark = if (settings.activeSetIndex == index) " ✓" else ""
                Text("セット ${index + 1}$selectedMark")
              }
            }
          }

          Text("現在のセットを別のセットへコピー")
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            repeat(X_CSS_SET_COUNT) { index ->
              if (index != settings.activeSetIndex) {
                TextButton(
                  onClick = {
                    settings = settings.copyCurrentCssTo(index)
                    copyMessage = "セット ${index + 1} にコピーしました（保存で確定）"
                  },
                ) {
                  Text("セット ${index + 1} へ")
                }
              }
            }
          }
          copyMessage?.let { Text(it) }

          OutlinedTextField(
            value = settings.css,
            onValueChange = { settings = settings.copy(css = it) },
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 320.dp),
            label = { Text("CSS（セット ${settings.activeSetIndex + 1}）") },
            enabled = settings.enabled,
            minLines = 14,
          )

          TextButton(
            onClick = {
              settings = settings.copy(enabled = true, css = defaultCss)
              copyMessage = null
            },
          ) {
            Text("このセットをデフォルト CSS に戻す")
          }

          Spacer(Modifier.height(12.dp))
          Text(
            text = "カスタム JavaScript",
            style = MaterialTheme.typography.titleMedium,
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text("カスタム JavaScript を有効化")
            Switch(
              checked = settings.javaScriptEnabled,
              onCheckedChange = {
                settings = settings.copy(javaScriptEnabled = it)
              },
            )
          }
          Text("X のページ読込後に保存した JavaScript を実行します")

          OutlinedTextField(
            value = settings.javaScript,
            onValueChange = { settings = settings.copy(javaScript = it) },
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 280.dp),
            label = { Text("JavaScript") },
            enabled = settings.javaScriptEnabled,
            minLines = 12,
          )

          Text(
            "JavaScript はこの X WebView 内でのみ実行します。Android の JavaScript bridge は公開しません。",
          )
          Text(
            "保存した CSS と JavaScript は次回の X ページ読込時に反映されます。JavaScript の副作用はページを再読み込みするまで残る場合があります。",
          )
          Spacer(Modifier.height(4.dp))
        }

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          TextButton(onClick = onDismiss) {
            Text("キャンセル")
          }
          TextButton(
            onClick = {
              repository.save(settings)
              onDismiss()
            },
          ) {
            Text("保存")
          }
        }
      }
    }
  }
}
