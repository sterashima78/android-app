package dev.terashima.yomitorirss.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.terashima.yomitorirss.core.designsystem.MarkdownText

@Composable
internal fun MarkdownMessage(
  content: String,
  modifier: Modifier = Modifier,
) {
  MarkdownText(
    content = content,
    modifier = modifier,
  )
}
