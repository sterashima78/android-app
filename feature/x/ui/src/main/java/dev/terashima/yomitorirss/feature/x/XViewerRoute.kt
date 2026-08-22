package dev.terashima.yomitorirss.feature.x

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun XViewerRoute(
  repository: XViewerCssRepository,
  modifier: Modifier = Modifier,
) {
  var showCssSettings by remember { mutableStateOf(false) }

  Box(modifier = modifier) {
    XViewerScreen(
      repository = repository,
      modifier = Modifier.fillMaxSize(),
    )
    Surface(
      modifier = Modifier
        .align(Alignment.TopEnd)
        .windowInsetsPadding(
          WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End),
        )
        .padding(8.dp),
      shape = MaterialTheme.shapes.large,
      color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
      tonalElevation = 4.dp,
    ) {
      IconButton(onClick = { showCssSettings = true }) {
        Icon(Icons.Default.Settings, contentDescription = "X カスタム CSS 設定")
      }
    }
  }

  if (showCssSettings) {
    XViewerCssSettingsSheet(
      repository = repository,
      onDismiss = { showCssSettings = false },
    )
  }
}
