package dev.terashima.yomitorirss.entry

import java.net.URI

internal data class SharedBookmark(
  val url: String,
  val title: String,
  val sourceTitle: String,
)

private val sharedUrlPattern = Regex("""https?://[^\s<>\"']+""", RegexOption.IGNORE_CASE)
private val trailingUrlCharacters = charArrayOf('.', ',', '、', '。', ')', '）', ']', '】', '}', '>', '"', '\'')

internal fun parseSharedBookmark(
  text: CharSequence?,
  subject: CharSequence?,
): SharedBookmark? {
  val rawText = text?.toString()?.trim().orEmpty()
  val match = sharedUrlPattern.find(rawText) ?: return null
  val url = match.value.trimEnd(*trailingUrlCharacters)
  val uri = runCatching { URI(url) }.getOrNull() ?: return null
  if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) return null

  val subjectTitle = subject?.toString()?.trim()?.takeIf { it.isNotBlank() && it != url }
  val textTitle = rawText
    .removeRange(match.range)
    .trim()
    .lineSequence()
    .firstOrNull(String::isNotBlank)
  val sourceTitle = uri.host.removePrefix("www.")

  return SharedBookmark(
    url = url,
    title = subjectTitle ?: textTitle ?: sourceTitle,
    sourceTitle = sourceTitle,
  )
}
