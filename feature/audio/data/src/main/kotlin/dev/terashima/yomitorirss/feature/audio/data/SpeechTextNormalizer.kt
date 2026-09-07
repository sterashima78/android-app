package dev.terashima.yomitorirss.feature.audio.data

internal fun markdownToSpeechText(markdown: String): String {
  val inlineNormalized = markdown
    .replace("\r\n", "\n")
    .replace('\r', '\n')
    .replace(MARKDOWN_IMAGE) { match -> match.groupValues[1] }
    .replace(MARKDOWN_LINK) { match -> match.groupValues[1] }
    .replace(MARKDOWN_REFERENCE_LINK) { match -> match.groupValues[1] }
    .replace(MARKDOWN_AUTOLINK, "")
    .replace(MARKDOWN_INLINE_CODE) { match -> match.groupValues[1] }
    .replace(MARKDOWN_STRONG_ASTERISK) { match -> match.groupValues[1] }
    .replace(MARKDOWN_STRONG_UNDERSCORE) { match -> match.groupValues[1] }
    .replace(MARKDOWN_STRIKETHROUGH) { match -> match.groupValues[1] }
    .replace(MARKDOWN_EMPHASIS_ASTERISK) { match -> match.groupValues[1] }
    .replace(MARKDOWN_EMPHASIS_UNDERSCORE) { match -> match.groupValues[1] }
    .replace(MARKDOWN_ESCAPE) { match -> match.groupValues[1] }

  return inlineNormalized
    .lineSequence()
    .mapNotNull(::normalizeMarkdownLineForSpeech)
    .joinToString("\n")
    .replace(EXCESS_BLANK_LINES, "\n\n")
    .trim()
}

private fun normalizeMarkdownLineForSpeech(line: String): String? {
  if (MARKDOWN_FENCE.matches(line) ||
    MARKDOWN_HORIZONTAL_RULE.matches(line) ||
    MARKDOWN_SETEXT_HEADING.matches(line) ||
    MARKDOWN_TABLE_DELIMITER.matches(line) ||
    MARKDOWN_REFERENCE_DEFINITION.matches(line)
  ) {
    return null
  }

  val withoutStructure = line
    .replace(MARKDOWN_BLOCKQUOTE_PREFIX, "")
    .replace(MARKDOWN_HEADING_PREFIX, "")
    .replace(MARKDOWN_HEADING_SUFFIX, "")
    .replace(MARKDOWN_UNORDERED_LIST_PREFIX, "")
    .replace(MARKDOWN_ORDERED_LIST_PREFIX, "")
    .replace(MARKDOWN_TASK_PREFIX, "")
    .trim()

  if ('|' !in withoutStructure) return withoutStructure

  return withoutStructure
    .split('|')
    .map { cell -> cell.trim() }
    .filter { cell -> cell.isNotEmpty() }
    .joinToString("、")
}

private val MARKDOWN_IMAGE = Regex("""!\[([^]]*)]\([^)]*\)""")
private val MARKDOWN_LINK = Regex("""\[([^]]+)]\([^)]*\)""")
private val MARKDOWN_REFERENCE_LINK = Regex("""\[([^]]+)]\[[^]]*]""")
private val MARKDOWN_AUTOLINK = Regex("""<https?://[^>]+>""")
private val MARKDOWN_INLINE_CODE = Regex("""`([^`\n]+)`""")
private val MARKDOWN_STRONG_ASTERISK = Regex("""\*\*([^*\n]+)\*\*""")
private val MARKDOWN_STRONG_UNDERSCORE = Regex("""(?<![\p{L}\p{N}_])__([^_\n]+)__(?![\p{L}\p{N}_])""")
private val MARKDOWN_STRIKETHROUGH = Regex("""~~([^~\n]+)~~""")
private val MARKDOWN_EMPHASIS_ASTERISK = Regex("""(?<!\*)\*([^*\n]+)\*(?!\*)""")
private val MARKDOWN_EMPHASIS_UNDERSCORE = Regex("""(?<![\p{L}\p{N}_])_([^_\n]+)_(?![\p{L}\p{N}_])""")
private val MARKDOWN_ESCAPE = Regex("""\\([\\`*_{}\[\]()#+\-.!>~|])""")
private val MARKDOWN_FENCE = Regex("""^\s{0,3}(?:`{3,}|~{3,}).*$""")
private val MARKDOWN_HORIZONTAL_RULE = Regex("""^\s{0,3}(?:(?:\*\s*){3,}|(?:-\s*){3,}|(?:_\s*){3,})$""")
private val MARKDOWN_SETEXT_HEADING = Regex("""^\s{0,3}=+\s*$""")
private val MARKDOWN_TABLE_DELIMITER = Regex("""^\s*\|?\s*:?-{3,}:?\s*(?:\|\s*:?-{3,}:?\s*)+\|?\s*$""")
private val MARKDOWN_REFERENCE_DEFINITION = Regex("""^\s{0,3}\[[^]]+]:\s*\S+.*$""")
private val MARKDOWN_HEADING_PREFIX = Regex("""^\s{0,3}#{1,6}\s+""")
private val MARKDOWN_HEADING_SUFFIX = Regex("""\s+#+\s*$""")
private val MARKDOWN_BLOCKQUOTE_PREFIX = Regex("""^(?:\s{0,3}>\s?)+""")
private val MARKDOWN_UNORDERED_LIST_PREFIX = Regex("""^\s{0,3}[-+*]\s+(?:\[[ xX]\]\s+)?""")
private val MARKDOWN_ORDERED_LIST_PREFIX = Regex("""^\s{0,3}\d+[.)]\s+""")
private val MARKDOWN_TASK_PREFIX = Regex("""^\s*\[[ xX]\]\s+""")
private val EXCESS_BLANK_LINES = Regex("""\n{3,}""")
