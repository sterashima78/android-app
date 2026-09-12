package dev.terashima.yomitorirss.feature.x

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
  var showCustomizationSettings by remember { mutableStateOf(false) }
  var fullscreenMediaVisible by remember { mutableStateOf(false) }

  Box(modifier = modifier) {
    XViewerMediaHost(
      repository = repository,
      onFullscreenChanged = { fullscreenMediaVisible = it },
      modifier = Modifier.fillMaxSize(),
    )
    if (!fullscreenMediaVisible) {
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
        IconButton(onClick = { showCustomizationSettings = true }) {
          Icon(Icons.Default.Settings, contentDescription = "X 表示カスタマイズ設定")
        }
      }

      Surface(
        modifier = Modifier
          .align(Alignment.BottomStart)
          .windowInsetsPadding(
            WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Start),
          )
          .padding(start = 12.dp, bottom = 76.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 6.dp,
      ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          XViewerMediaDiagnosticsButton()
          XViewerOutlineClipDiagnosticButton()
        }
      }
    }
  }

  if (showCustomizationSettings && !fullscreenMediaVisible) {
    XViewerCustomizationDialog(
      repository = repository,
      onDismiss = { showCustomizationSettings = false },
    )
  }
}
