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

internal const val X_CUSTOM_CSS_ASSET = "x_viewer.css"
internal const val X_CSS_SET_COUNT = 3

private fun initialCssSets(css: String, activeSetIndex: Int): List<String> =
  List(X_CSS_SET_COUNT) { index -> if (index == activeSetIndex) css else "" }

internal data class XViewerCssSettings(
  val enabled: Boolean,
  val css: String,
  val activeSetIndex: Int = 0,
  private val cssSets: List<String> = initialCssSets(css, activeSetIndex),
) {
  init {
    require(cssSets.size == X_CSS_SET_COUNT)
    require(activeSetIndex in 0 until X_CSS_SET_COUNT)
  }

  fun selectSet(index: Int): XViewerCssSettings {
    require(index in 0 until X_CSS_SET_COUNT)
    if (index == activeSetIndex) return this

    val updatedSets = persistedCssSets()
    return XViewerCssSettings(
      enabled = enabled,
      css = updatedSets[index],
      activeSetIndex = index,
      cssSets = updatedSets,
    )
  }

  fun copyCurrentCssTo(targetSetIndex: Int): XViewerCssSettings {
    require(targetSetIndex in 0 until X_CSS_SET_COUNT)
    if (targetSetIndex == activeSetIndex) return this

    val updatedSets = persistedCssSets().toMutableList().apply {
      this[targetSetIndex] = css
    }
    return copy(cssSets = updatedSets)
  }

  fun cssAt(index: Int): String {
    require(index in 0 until X_CSS_SET_COUNT)
    return if (index == activeSetIndex) css else cssSets[index]
  }

  fun persistedCssSets(): List<String> = cssSets.toMutableList().apply {
    this[activeSetIndex] = css
  }
}

internal object XViewerCssPreferences {
  private const val PREFS_NAME = "x_viewer_preferences"
  private const val KEY_CSS_ENABLED = "custom_css_enabled"
  private const val KEY_LEGACY_CUSTOM_CSS = "custom_css"
  private const val KEY_ACTIVE_CSS_SET = "active_css_set"
  private const val KEY_CUSTOM_CSS_SET_PREFIX = "custom_css_set_"

  private fun cssSetKey(index: Int): String = "$KEY_CUSTOM_CSS_SET_PREFIX${index + 1}"

  fun load(context: Context, defaultCss: String): XViewerCssSettings {
    val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val cssSets = List(X_CSS_SET_COUNT) { index ->
      val key = cssSetKey(index)
      when {
        preferences.contains(key) -> preferences.getString(key, "") ?: ""
        index == 0 && preferences.contains(KEY_LEGACY_CUSTOM_CSS) ->
          preferences.getString(KEY_LEGACY_CUSTOM_CSS, defaultCss) ?: defaultCss
        index == 0 -> defaultCss
        else -> ""
      }
    }
    val activeSetIndex = preferences.getInt(KEY_ACTIVE_CSS_SET, 0)
      .coerceIn(0, X_CSS_SET_COUNT - 1)

    return XViewerCssSettings(
      enabled = preferences.getBoolean(KEY_CSS_ENABLED, true),
      css = cssSets[activeSetIndex],
      activeSetIndex = activeSetIndex,
      cssSets = cssSets,
    )
  }

  fun save(context: Context, settings: XViewerCssSettings) {
    val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putBoolean(KEY_CSS_ENABLED, settings.enabled)
      .putInt(KEY_ACTIVE_CSS_SET, settings.activeSetIndex)
      .remove(KEY_LEGACY_CUSTOM_CSS)

    settings.persistedCssSets().forEachIndexed { index, css ->
      editor.putString(cssSetKey(index), css)
    }
    editor.apply()
  }
}

internal fun Context.readDefaultXViewerCss(): String =
  assets.open(X_CUSTOM_CSS_ASSET).bufferedReader().use { it.readText() }

internal fun XViewerCssSettings.cssForInjection(): String = if (enabled) css else ""

@Composable
fun XViewerCssSettingsSheet(onDismiss: () -> Unit) {
  val context = LocalContext.current
  val defaultCss = remember(context) { context.readDefaultXViewerCss() }
  val savedSettings = remember(context) { XViewerCssPreferences.load(context, defaultCss) }
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
            XViewerCssPreferences.save(context, settings)
            onDismiss()
          },
        ) {
          Text("保存")
        }
      }
    }
  }
}
