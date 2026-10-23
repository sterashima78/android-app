package dev.terashima.yomitorirss.feature.rss

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** Shared selector; null denotes the inherited refresh interval. */
@Composable
internal fun RssRefreshIntervalPicker(
  selectedMinutes: Long?,
  inheritedLabel: String,
  allowInheritance: Boolean,
  options: List<Long> = RSS_REFRESH_INTERVAL_OPTIONS,
  onSelect: (Long?) -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }
  Box {
    TextButton(onClick = { expanded = true }) {
      Text(selectedMinutes?.let(::rssRefreshIntervalLabel) ?: inheritedLabel)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      if (allowInheritance) {
        DropdownMenuItem(text = { Text(inheritedLabel) }, onClick = {
          expanded = false
          onSelect(null)
        })
      }
      options.forEach { minutes ->
        DropdownMenuItem(text = { Text(rssRefreshIntervalLabel(minutes)) }, onClick = {
          expanded = false
          onSelect(minutes)
        })
      }
    }
  }
}
