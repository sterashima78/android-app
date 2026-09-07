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
    .map(::normalizeMarkdownLineForSpeech)
    .joinToString("\n")
    .replace(EXCESS_BLANK_LINES, "\n\n")
    .trim()
}

private fun normalizeMarkdownLineForSpeech(line: String): String {
  if (MARKDOWN_FENCE.matches(line) ||
    MARKDOWN_HORIZONTAL_RULE.matches(line) ||
    MARKDOWN_TABLE_DELIMITER.matches(line)
  ) {
    return ""
  }

  val withoutStructure = line
    .replace(MARKDOWN_HEADING_PREFIX, "")
    .replace(MARKDOWN_BLOCKQUOTE_PREFIX, "")
    .replace(MARKDOWN_UNORDERED_LIST_PREFIX, "")
    .replace(MARKDOWN_ORDERED_LIST_PREFIX, "")
    .trim()

  if ('|' !in withoutStructure) return withoutStructure

  return withoutStructure
    .split('|')
    .map(String::trim)
    .filter(String::isNotEmpty)
    .joinToString("、")
}

private val MARKDOWN_IMAGE = Regex("""!\[([^]]*)]\([^)]*\)""")
private val MARKDOWN_LINK = Regex("""\[([^]]+)]\([^)]*\)""")
private val MARKDOWN_REFERENCE_LINK = Regex("""\[([^]]+)]\[[^]]*]""")
private val MARKDOWN_AUTOLINK = Regex("""<https?://[^>]+>""")
private val MARKDOWN_INLINE_CODE = Regex("""`([^`\n]+)`""")
private val MARKDOWN_STRONG_ASTERISK = Regex("""\*\*([^*\n]+)\*\*""")
private val MARKDOWN_STRONG_UNDERSCORE = Regex("""__([^_\n]+)__""")
private val MARKDOWN_STRIKETHROUGH = Regex("""~~([^~\n]+)~~""")
private val MARKDOWN_EMPHASIS_ASTERISK = Regex("""(?<!\*)\*([^*\n]+)\*(?!\*)""")
private val MARKDOWN_EMPHASIS_UNDERSCORE = Regex("""(?<!_)_([^_\n]+)_(?!_)""")
private val MARKDOWN_ESCAPE = Regex("""\\([\\`*_{}\[\]()#+\-.!>~|])""")
private val MARKDOWN_FENCE = Regex("""^\s{0,3}(?:`{3,}|~{3,}).*$""")
private val MARKDOWN_HORIZONTAL_RULE = Regex("""^\s{0,3}(?:(?:\*\s*){3,}|(?:-\s*){3,}|(?:_\s*){3,})$""")
private val MARKDOWN_TABLE_DELIMITER = Regex("""^\s*\|?\s*:?-{3,}:?\s*(?:\|\s*:?-{3,}:?\s*)+\|?\s*$""")
private val MARKDOWN_HEADING_PREFIX = Regex("""^\s{0,3}#{1,6}\s+""")
private val MARKDOWN_BLOCKQUOTE_PREFIX = Regex("""^(?:\s{0,3}>\s?)+""")
private val MARKDOWN_UNORDERED_LIST_PREFIX = Regex("""^\s{0,3}[-+*]\s+(?:\[[ xX]\]\s+)?""")
private val MARKDOWN_ORDERED_LIST_PREFIX = Regex("""^\s{0,3}\d+[.)]\s+""")
private val EXCESS_BLANK_LINES = Regex("""\n{3,}""")
