package dev.terashima.yomitorirss.feature.x

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

internal const val X_CUSTOM_CSS_ASSET = "x_viewer.css"

internal data class XViewerCssSettings(
  val enabled: Boolean,
  val css: String,
)

internal object XViewerCssPreferences {
  private const val PREFS_NAME = "x_viewer_preferences"
  private const val KEY_CSS_ENABLED = "custom_css_enabled"
  private const val KEY_CUSTOM_CSS = "custom_css"

  fun load(context: Context, defaultCss: String): XViewerCssSettings {
    val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return XViewerCssSettings(
      enabled = preferences.getBoolean(KEY_CSS_ENABLED, true),
      css = if (preferences.contains(KEY_CUSTOM_CSS)) {
        preferences.getString(KEY_CUSTOM_CSS, defaultCss) ?: defaultCss
      } else {
        defaultCss
      },
    )
  }

  fun save(context: Context, settings: XViewerCssSettings) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putBoolean(KEY_CSS_ENABLED, settings.enabled)
      .putString(KEY_CUSTOM_CSS, settings.css)
      .apply()
  }
}

internal fun Context.readDefaultXViewerCss(): String =
  assets.open(X_CUSTOM_CSS_ASSET).bufferedReader().use { it.readText() }

internal fun XViewerCssSettings.cssForInjection(): String = if (enabled) css else ""

@Composable
fun XViewerCssSettingsDialog(onDismiss: () -> Unit) {
  val context = LocalContext.current
  val defaultCss = remember(context) { context.readDefaultXViewerCss() }
  val savedSettings = remember(context) { XViewerCssPreferences.load(context, defaultCss) }
  var enabled by remember(savedSettings) { mutableStateOf(savedSettings.enabled) }
  var css by remember(savedSettings) { mutableStateOf(savedSettings.css) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("X カスタム CSS") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text("カスタム CSS を有効化")
          Switch(
            checked = enabled,
            onCheckedChange = { enabled = it },
          )
        }
        Text("無効にすると CSS を一切注入しません")

        OutlinedTextField(
          value = css,
          onValueChange = { css = it },
          modifier = Modifier.fillMaxWidth().heightIn(min = 280.dp),
          label = { Text("CSS") },
          enabled = enabled,
          minLines = 12,
        )

        TextButton(
          onClick = {
            enabled = true
            css = defaultCss
          },
        ) {
          Text("デフォルト CSS に戻す")
        }

        Text("保存後、X 画面を開き直すと反映されます。")
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          XViewerCssPreferences.save(
            context,
            XViewerCssSettings(enabled = enabled, css = css),
          )
          onDismiss()
        },
      ) {
        Text("保存")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("キャンセル")
      }
    },
  )
}
