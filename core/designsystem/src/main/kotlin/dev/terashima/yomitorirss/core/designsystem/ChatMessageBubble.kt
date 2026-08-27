package dev.terashima.yomitorirss.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ChatMessageBubble(
  isUser: Boolean,
  content: String,
  modifier: Modifier = Modifier,
  userLabel: String = "あなた",
  assistantLabel: String = "AI",
) {
  Box(modifier = modifier.fillMaxWidth()) {
    Surface(
      modifier = Modifier
        .align(if (isUser) Alignment.CenterEnd else Alignment.CenterStart)
        .widthIn(max = 360.dp),
      shape = RoundedCornerShape(14.dp),
      color = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
      } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
      },
    ) {
      Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(
          text = if (isUser) userLabel else assistantLabel,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (isUser) {
          Text(content, style = MaterialTheme.typography.bodyMedium)
        } else {
          MarkdownText(content)
        }
      }
    }
  }
}
