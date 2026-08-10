package dev.terashima.yomitorirss.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val YomitoriDarkColors = darkColorScheme(
  primary = Color(0xFF74C7EC),
  onPrimary = Color(0xFF002B3A),
  secondary = Color(0xFFA6E3A1),
  tertiary = Color(0xFFF9E2AF),
  background = Color(0xFF0B0F14),
  surface = Color(0xFF111820),
  surfaceVariant = Color(0xFF1A232D),
  onSurface = Color(0xFFE6EDF3),
  onSurfaceVariant = Color(0xFF9EABB8),
  error = Color(0xFFF38BA8),
)

@Composable
fun YomitoriTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = YomitoriDarkColors,
    content = content,
  )
}
