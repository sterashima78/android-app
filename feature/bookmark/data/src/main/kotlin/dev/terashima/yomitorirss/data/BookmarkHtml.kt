package dev.terashima.yomitorirss.feature.bookmark.data
import org.jsoup.Jsoup
import java.io.Reader
import java.net.URI
import java.time.Instant

internal data class BookmarkHtmlEntry(
  val title: String,
  val url: String,
  val createdAt: String,
  val sourceTitle: String,
  val tagNames: List<String>,
)

internal data class BookmarkHtmlParseResult(
  val entries: List<BookmarkHtmlEntry>,
  val skippedEntries: Int,
)

internal fun parseBookmarkHtml(
  reader: Reader,
  fallbackCreatedAt: String = Instant.now().toString(),
): BookmarkHtmlParseResult {
  val html = reader.readText()
  require(html.isNotBlank()) { "HTMLが空です" }

  val folderStack = ArrayDeque<String>()
  val dlFolderPushes = ArrayDeque<Boolean>()
  val entries = mutableListOf<BookmarkHtmlEntry>()
  var pendingFolder: String? = null
  var skippedEntries = 0
  var sawBookmarkContainer = false

  bookmarkHtmlToken.findAll(html).forEach { match ->
    val token = match.value
    when {
      closingDl.containsMatchIn(token) -> {
        if (dlFolderPushes.isNotEmpty()) {
          val pushedFolder = dlFolderPushes.removeLast()
          if (pushedFolder && folderStack.isNotEmpty()) folderStack.removeLast()
        }
        pendingFolder = null
      }
      openingDl.containsMatchIn(token) -> {
        sawBookmarkContainer = true
        val folder = pendingFolder?.trim()?.takeIf(String::isNotBlank)
        if (folder != null) folderStack.addLast(folder)
        dlFolderPushes.addLast(folder != null)
        pendingFolder = null
      }
      openingH3.containsMatchIn(token) -> {
        pendingFolder = Jsoup.parseBodyFragment(token)
          .selectFirst("h3")
          ?.text()
          ?.trim()
          ?.takeIf(String::isNotBlank)
      }
      openingAnchor.containsMatchIn(token) -> {
        val link = Jsoup.parseBodyFragment(token).selectFirst("a")
        if (link == null) {
          skippedEntries += 1
          return@forEach
        }

        val rawUrl = link.attr("href").trim()
        val uri = runCatching { URI(rawUrl) }.getOrNull()
        val host = uri?.host?.takeIf(String::isNotBlank)
        if (uri?.scheme?.lowercase() !in setOf("http", "https") || host == null) {
          skippedEntries += 1
          return@forEach
        }

        val sourceTitle = host.removePrefix("www.")
        val title = link.text().trim().ifBlank { sourceTitle }
        val createdAt = link.attr("add_date")
          .trim()
          .toLongOrNull()
          ?.let { seconds -> runCatching { Instant.ofEpochSecond(seconds).toString() }.getOrNull() }
          ?: fallbackCreatedAt
        val tagNames = folderStack
          .map(String::trim)
          .filter(String::isNotBlank)
          .distinctBy(::normalizeHtmlImportedTag)

        entries += BookmarkHtmlEntry(
          title = title,
          url = rawUrl,
          createdAt = createdAt,
          sourceTitle = sourceTitle,
          tagNames = tagNames,
        )
      }
    }
  }

  require(sawBookmarkContainer || html.contains("NETSCAPE-Bookmark-file-1", ignoreCase = true)) {
    "ブックマークHTML形式ではありません"
  }

  return BookmarkHtmlParseResult(entries = entries, skippedEntries = skippedEntries)
}

private fun normalizeHtmlImportedTag(name: String): String =
  name.trim().replace(Regex("\\s+"), " ").lowercase()

private val bookmarkHtmlToken = Regex(
  """(?is)<\s*/\s*dl\s*>|<\s*dl\b[^>]*>|<\s*h3\b[^>]*>.*?<\s*/\s*h3\s*>|<\s*a\b[^>]*>.*?<\s*/\s*a\s*>""",
)
private val closingDl = Regex("""(?is)^<\s*/\s*dl\b""")
private val openingDl = Regex("""(?is)^<\s*dl\b""")
private val openingH3 = Regex("""(?is)^<\s*h3\b""")
private val openingAnchor = Regex("""(?is)^<\s*a\b""")
