@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.terashima.yomitorirss.feature.x

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun XViewerCssSettingsSheet(
  repository: XViewerCssRepository,
  onDismiss: () -> Unit,
) {
  val defaultCss = remember(repository) { repository.defaultCss() }
  val savedSettings = remember(repository) { repository.load() }
  var settings by remember(savedSettings) { mutableStateOf(savedSettings) }
  var copyMessage by remember { mutableStateOf<String?>(null) }
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .fillMaxHeight(0.92f)
        .imePadding()
        .padding(horizontal = 24.dp),
    ) {
      Text(
        text = "X 表示カスタマイズ",
        style = MaterialTheme.typography.headlineSmall,
      )
      Spacer(Modifier.height(12.dp))

      Column(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text("表示カスタマイズを有効化")
          Switch(
            checked = settings.enabled,
            onCheckedChange = { settings = settings.copy(enabled = it) },
          )
        }
        Text("無効にすると CSS と要素表示ルールを適用しません")

        Text("要素表示ルール ${settings.domRules.size} 件")
        Text("「同じ列のリストだけ表示」で作成したルールは、X の DOM 更新後も再適用されます。")
        TextButton(
          onClick = { settings = settings.clearDomRules() },
          enabled = settings.domRules.isNotEmpty(),
        ) {
          Text("要素表示ルールをすべて削除")
        }

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

        Text("保存後、選択中の CSS セットと要素表示ルールが次回 X 画面を開いたときに反映されます。")
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
