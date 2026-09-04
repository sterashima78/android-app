package dev.terashima.yomitorirss.feature.task

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink

private val taskDescriptionUrlRegex = Regex("https?://\\S+")
private val taskDescriptionTrailingPunctuation = setOf(
  '.',
  ',',
  ';',
  ':',
  '!',
  '?',
  ')',
  ']',
  '}',
  '>',
  '"',
  '\'',
  '。',
  '、',
  '，',
  '；',
  '：',
  '！',
  '？',
  '）',
  '］',
  '｝',
  '〉',
  '》',
  '」',
  '』',
  '】',
)

internal data class TaskDescriptionUrl(
  val value: String,
  val range: IntRange,
)

internal fun findTaskDescriptionUrls(description: String): List<TaskDescriptionUrl> =
  taskDescriptionUrlRegex.findAll(description).mapNotNull { match ->
    val value = match.value.trimEnd { it in taskDescriptionTrailingPunctuation }
    if (value.length <= "https://".length) return@mapNotNull null
    TaskDescriptionUrl(
      value = value,
      range = match.range.first..(match.range.first + value.lastIndex),
    )
  }.toList()

internal fun taskDescriptionAnnotatedString(
  description: String,
  linkColor: Color,
): AnnotatedString = buildAnnotatedString {
  var cursor = 0
  findTaskDescriptionUrls(description).forEach { url ->
    if (cursor < url.range.first) {
      append(description.substring(cursor, url.range.first))
    }
    withLink(
      LinkAnnotation.Url(
        url = url.value,
        styles = TextLinkStyles(
          style = SpanStyle(
            color = linkColor,
            textDecoration = TextDecoration.Underline,
          ),
        ),
      ),
    ) {
      append(url.value)
    }
    cursor = url.range.last + 1
  }
  if (cursor < description.length) {
    append(description.substring(cursor))
  }
}

@Composable
internal fun TaskDescriptionText(
  description: String,
) {
  Text(
    text = taskDescriptionAnnotatedString(
      description = description,
      linkColor = MaterialTheme.colorScheme.primary,
    ),
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines = 3,
  )
}
