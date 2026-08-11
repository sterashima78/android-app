package dev.terashima.yomitorirss.feature.chat

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

internal sealed interface MarkdownBlock {
  data class Paragraph(val text: String) : MarkdownBlock
  data class Heading(val level: Int, val text: String) : MarkdownBlock
  data class ListBlock(val ordered: Boolean, val items: List<MarkdownListItem>) : MarkdownBlock
  data class Quote(val text: String) : MarkdownBlock
  data class CodeBlock(val language: String?, val code: String) : MarkdownBlock
  data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownBlock
  data object Divider : MarkdownBlock
}

internal data class MarkdownListItem(
  val indent: Int,
  val marker: String,
  val text: String,
)

@Composable
internal fun MarkdownMessage(
  content: String,
  modifier: Modifier = Modifier,
) {
  val blocks = remember(content) { parseMarkdownBlocks(content) }
  val linkColor = MaterialTheme.colorScheme.primary
  val codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    blocks.forEach { block ->
      when (block) {
        is MarkdownBlock.Paragraph -> Text(
          text = markdownInlineText(block.text, linkColor, codeBackground),
          style = MaterialTheme.typography.bodyMedium,
        )

        is MarkdownBlock.Heading -> Text(
          text = markdownInlineText(block.text, linkColor, codeBackground),
          style = when (block.level) {
            1 -> MaterialTheme.typography.titleLarge
            2 -> MaterialTheme.typography.titleMedium
            3 -> MaterialTheme.typography.titleSmall
            else -> MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
          },
        )

        is MarkdownBlock.ListBlock -> MarkdownList(
          block = block,
          linkColor = linkColor,
          codeBackground = codeBackground,
        )

        is MarkdownBlock.Quote -> Surface(
          color = MaterialTheme.colorScheme.surfaceContainerLow,
          shape = RoundedCornerShape(6.dp),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = "│",
              color = MaterialTheme.colorScheme.primary,
              style = MaterialTheme.typography.bodyMedium,
            )
            Text(
              text = markdownInlineText(block.text, linkColor, codeBackground),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        is MarkdownBlock.CodeBlock -> MarkdownCodeBlock(block)
        is MarkdownBlock.Table -> MarkdownTable(block, linkColor, codeBackground)
        MarkdownBlock.Divider -> HorizontalDivider()
      }
    }
  }
}

@Composable
private fun MarkdownList(
  block: MarkdownBlock.ListBlock,
  linkColor: androidx.compose.ui.graphics.Color,
  codeBackground: androidx.compose.ui.graphics.Color,
) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    block.items.forEach { item ->
      val task = TASK_ITEM.matchEntire(item.text)
      val marker = when {
        task != null -> if (task.groupValues[1].equals("x", ignoreCase = true)) "☑" else "☐"
        block.ordered -> "${item.marker}."
        else -> "•"
      }
      val text = task?.groupValues?.get(2) ?: item.text
      Row(
        modifier = Modifier.padding(start = (item.indent * 12).dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          text = marker,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = markdownInlineText(text, linkColor, codeBackground),
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@Composable
private fun MarkdownCodeBlock(block: MarkdownBlock.CodeBlock) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerHighest,
    shape = RoundedCornerShape(8.dp),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(horizontal = 12.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      block.language?.takeIf { it.isNotBlank() }?.let { language ->
        Text(
          text = language,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Text(
        text = block.code,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
      )
    }
  }
}

@Composable
private fun MarkdownTable(
  block: MarkdownBlock.Table,
  linkColor: androidx.compose.ui.graphics.Color,
  codeBackground: androidx.compose.ui.graphics.Color,
) {
  Column(
    modifier = Modifier.horizontalScroll(rememberScrollState()),
  ) {
    MarkdownTableRow(block.headers, isHeader = true, linkColor, codeBackground)
    block.rows.forEach { row ->
      MarkdownTableRow(row, isHeader = false, linkColor, codeBackground)
    }
  }
}

@Composable
private fun MarkdownTableRow(
  cells: List<String>,
  isHeader: Boolean,
  linkColor: androidx.compose.ui.graphics.Color,
  codeBackground: androidx.compose.ui.graphics.Color,
) {
  Row {
    cells.forEach { cell ->
      Text(
        text = markdownInlineText(cell, linkColor, codeBackground),
        style = if (isHeader) {
          MaterialTheme.typography.labelLarge
        } else {
          MaterialTheme.typography.bodySmall
        },
        modifier = Modifier
          .width(160.dp)
          .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
          .padding(horizontal = 8.dp, vertical = 7.dp),
      )
    }
  }
}

internal fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
  val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split('\n')
  val blocks = mutableListOf<MarkdownBlock>()
  val paragraph = mutableListOf<String>()

  fun flushParagraph() {
    if (paragraph.isNotEmpty()) {
      blocks += MarkdownBlock.Paragraph(paragraph.joinToString(" ") { it.trim() })
      paragraph.clear()
    }
  }

  var index = 0
  while (index < lines.size) {
    val line = lines[index]
    if (line.isBlank()) {
      flushParagraph()
      index += 1
      continue
    }

    CODE_FENCE.matchEntire(line)?.let { match ->
      flushParagraph()
      val fence = match.groupValues[1]
      val language = match.groupValues[2].trim().ifBlank { null }
      val code = mutableListOf<String>()
      index += 1
      while (index < lines.size && !lines[index].trimStart().startsWith(fence)) {
        code += lines[index]
        index += 1
      }
      if (index < lines.size) index += 1
      blocks += MarkdownBlock.CodeBlock(language, code.joinToString("\n"))
      continue
    }

    HEADING.matchEntire(line)?.let { match ->
      flushParagraph()
      blocks += MarkdownBlock.Heading(match.groupValues[1].length, match.groupValues[2].trim())
      index += 1
      continue
    }

    if (isDivider(line)) {
      flushParagraph()
      blocks += MarkdownBlock.Divider
      index += 1
      continue
    }

    if (index + 1 < lines.size && isTableSeparator(lines[index + 1]) && line.contains('|')) {
      flushParagraph()
      val headers = splitTableRow(line)
      val rows = mutableListOf<List<String>>()
      index += 2
      while (index < lines.size && lines[index].contains('|') && lines[index].isNotBlank()) {
        rows += splitTableRow(lines[index])
        index += 1
      }
      blocks += MarkdownBlock.Table(headers, rows)
      continue
    }

    QUOTE.matchEntire(line)?.let {
      flushParagraph()
      val quoteLines = mutableListOf<String>()
      while (index < lines.size) {
        val match = QUOTE.matchEntire(lines[index]) ?: break
        quoteLines += match.groupValues[1]
        index += 1
      }
      blocks += MarkdownBlock.Quote(quoteLines.joinToString("\n"))
      continue
    }

    UNORDERED_LIST.matchEntire(line)?.let {
      flushParagraph()
      val items = mutableListOf<MarkdownListItem>()
      while (index < lines.size) {
        val match = UNORDERED_LIST.matchEntire(lines[index]) ?: break
        items += MarkdownListItem(
          indent = markdownIndent(match.groupValues[1]),
          marker = match.groupValues[2],
          text = match.groupValues[3],
        )
        index += 1
      }
      blocks += MarkdownBlock.ListBlock(ordered = false, items = items)
      continue
    }

    ORDERED_LIST.matchEntire(line)?.let {
      flushParagraph()
      val items = mutableListOf<MarkdownListItem>()
      while (index < lines.size) {
        val match = ORDERED_LIST.matchEntire(lines[index]) ?: break
        items += MarkdownListItem(
          indent = markdownIndent(match.groupValues[1]),
          marker = match.groupValues[2],
          text = match.groupValues[3],
        )
        index += 1
      }
      blocks += MarkdownBlock.ListBlock(ordered = true, items = items)
      continue
    }

    paragraph += line
    index += 1
  }

  flushParagraph()
  return blocks
}

internal fun isSafeMarkdownLink(url: String): Boolean {
  val normalized = url.trim().lowercase()
  return normalized.startsWith("https://") ||
    normalized.startsWith("http://") ||
    normalized.startsWith("mailto:")
}

private fun markdownIndent(prefix: String): Int =
  prefix.fold(0) { count, char -> count + if (char == '\t') 2 else 1 } / 2

private fun isDivider(line: String): Boolean = DIVIDER.matches(line.trim())

private fun isTableSeparator(line: String): Boolean {
  val cells = splitTableRow(line)
  return cells.isNotEmpty() && cells.all { TABLE_SEPARATOR_CELL.matches(it.trim()) }
}

private fun splitTableRow(line: String): List<String> =
  line.trim().trim('|').split('|').map { it.trim() }

private fun markdownInlineText(
  text: String,
  linkColor: androidx.compose.ui.graphics.Color,
  codeBackground: androidx.compose.ui.graphics.Color,
): AnnotatedString = buildAnnotatedString {
  var index = 0
  while (index < text.length) {
    if (text[index] == '\\' && index + 1 < text.length) {
      append(text[index + 1])
      index += 2
      continue
    }

    if (text[index] == '`') {
      val end = text.indexOf('`', index + 1)
      if (end > index + 1) {
        withStyle(
          SpanStyle(
            fontFamily = FontFamily.Monospace,
            background = codeBackground,
          ),
        ) {
          append(text.substring(index + 1, end))
        }
        index = end + 1
        continue
      }
    }

    if (text[index] == '[') {
      val labelEnd = text.indexOf("](", index + 1)
      if (labelEnd > index + 1) {
        val urlStart = labelEnd + 2
        val urlEnd = text.indexOf(')', urlStart)
        if (urlEnd > urlStart) {
          val label = text.substring(index + 1, labelEnd)
          val url = text.substring(urlStart, urlEnd).trim()
          if (isSafeMarkdownLink(url)) {
            withLink(
              LinkAnnotation.Url(
                url = url,
                styles = TextLinkStyles(
                  style = SpanStyle(
                    color = linkColor,
                    textDecoration = TextDecoration.Underline,
                  ),
                ),
              ),
            ) {
              append(label)
            }
          } else {
            append(text.substring(index, urlEnd + 1))
          }
          index = urlEnd + 1
          continue
        }
      }
    }

    if (text[index] == '<') {
      val end = text.indexOf('>', index + 1)
      if (end > index + 1) {
        val url = text.substring(index + 1, end)
        if (isSafeMarkdownLink(url)) {
          withLink(
            LinkAnnotation.Url(
              url = url,
              styles = TextLinkStyles(
                style = SpanStyle(
                  color = linkColor,
                  textDecoration = TextDecoration.Underline,
                ),
              ),
            ),
          ) {
            append(url)
          }
          index = end + 1
          continue
        }
      }
    }

    if (text.startsWith("**", index)) {
      val end = text.indexOf("**", index + 2)
      if (end > index + 2) {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
          append(text.substring(index + 2, end))
        }
        index = end + 2
        continue
      }
    }

    if (text.startsWith("~~", index)) {
      val end = text.indexOf("~~", index + 2)
      if (end > index + 2) {
        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
          append(text.substring(index + 2, end))
        }
        index = end + 2
        continue
      }
    }

    if (text[index] == '*') {
      val end = text.indexOf('*', index + 1)
      if (end > index + 1) {
        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
          append(text.substring(index + 1, end))
        }
        index = end + 1
        continue
      }
    }

    append(text[index])
    index += 1
  }
}

private val CODE_FENCE = Regex("""^\s*(```|~~~)(.*)$""")
private val HEADING = Regex("""^\s*(#{1,6})\s+(.+)$""")
private val QUOTE = Regex("""^\s*>\s?(.*)$""")
private val UNORDERED_LIST = Regex("""^(\s*)([-+*])\s+(.+)$""")
private val ORDERED_LIST = Regex("""^(\s*)(\d+)[.)]\s+(.+)$""")
private val TASK_ITEM = Regex("""^\[([ xX])]\s+(.+)$""")
private val DIVIDER = Regex("""^(?:-{3,}|\*{3,}|_{3,})$""")
private val TABLE_SEPARATOR_CELL = Regex("""^:?-{3,}:?$""")
