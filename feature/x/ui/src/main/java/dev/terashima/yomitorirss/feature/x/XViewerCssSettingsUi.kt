@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.terashima.yomitorirss.feature.x

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private fun Context.requireXViewerCssRepository(): XViewerCssRepository {
  val provider = applicationContext as? XViewerCssRepositoryProvider
    ?: error("Application must implement XViewerCssRepositoryProvider")
  return provider.xViewerCssRepository
}

internal object XViewerCssPreferences {
  @Suppress("UNUSED_PARAMETER")
  fun load(context: Context, defaultCss: String): XViewerCssSettings =
    context.requireXViewerCssRepository().load()

  fun save(context: Context, settings: XViewerCssSettings) {
    context.requireXViewerCssRepository().save(settings)
  }
}

internal fun Context.readDefaultXViewerCss(): String =
  requireXViewerCssRepository().defaultCss()

@Composable
fun XViewerCssSettingsSheet(onDismiss: () -> Unit) {
  val context = LocalContext.current
  XViewerCssSettingsSheet(
    repository = context.requireXViewerCssRepository(),
    onDismiss = onDismiss,
  )
}

@Composable
private fun XViewerCssSettingsSheet(
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
        text = "X カスタム CSS",
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

        Text("保存後、選択中のセットが次回 X 画面を開いたときに反映されます。")
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
