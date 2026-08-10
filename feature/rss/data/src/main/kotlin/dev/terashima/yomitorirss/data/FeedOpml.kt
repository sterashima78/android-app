package dev.terashima.yomitorirss.feature.rss.data

import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.Reader
import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory

data class OpmlFeed(
  val title: String,
  val feedUrl: String,
  val siteUrl: String?,
  val folders: List<String>,
)

data class OpmlParseResult(
  val feeds: List<OpmlFeed>,
  val duplicates: Int,
  val skipped: Int,
)

fun parseFeedOpml(reader: Reader): OpmlParseResult {
  val document = secureDocumentBuilderFactory()
    .newDocumentBuilder()
    .parse(InputSource(reader))
  val root = document.documentElement ?: error("OPMLのルート要素がありません")
  require(root.tagName.equals("opml", ignoreCase = true)) { "OPMLファイルではありません" }
  val body = root.childElements().firstOrNull { it.tagName.equals("body", ignoreCase = true) }
    ?: error("OPMLのbody要素がありません")

  val uniqueFeeds = linkedMapOf<String, OpmlFeed>()
  var duplicates = 0
  var skipped = 0

  fun visit(outline: Element, folders: List<String>) {
    val xmlUrl = outline.attributeIgnoreCase("xmlUrl").trim()
    val label = outline.attributeIgnoreCase("title")
      .ifBlank { outline.attributeIgnoreCase("text") }
      .trim()
    val nextFolders = if (xmlUrl.isBlank() && label.isNotBlank()) folders + label else folders

    if (xmlUrl.isNotBlank()) {
      val validated = validateFeedUrl(xmlUrl)
      if (validated == null) {
        skipped += 1
      } else {
        val feed = OpmlFeed(
          title = label.ifBlank { validated.value },
          feedUrl = validated.value,
          siteUrl = outline.attributeIgnoreCase("htmlUrl").trim().ifBlank { null },
          folders = folders,
        )
        if (uniqueFeeds.putIfAbsent(validated.key, feed) != null) duplicates += 1
      }
    }

    outline.childElements()
      .filter { it.tagName.equals("outline", ignoreCase = true) }
      .forEach { visit(it, nextFolders) }
  }

  body.childElements()
    .filter { it.tagName.equals("outline", ignoreCase = true) }
    .forEach { visit(it, emptyList()) }

  return OpmlParseResult(
    feeds = uniqueFeeds.values.toList(),
    duplicates = duplicates,
    skipped = skipped,
  )
}

fun normalizedFeedUrlKey(value: String): String? = validateFeedUrl(value)?.key

private data class ValidatedFeedUrl(val value: String, val key: String)

private fun validateFeedUrl(value: String): ValidatedFeedUrl? = runCatching {
  val parsed = URI(value.trim())
  val scheme = parsed.scheme?.lowercase()
  require(scheme == "http" || scheme == "https")
  val authority = parsed.rawAuthority?.takeIf(String::isNotBlank) ?: error("ホスト名がありません")
  val path = parsed.rawPath.orEmpty().ifBlank { "/" }
  val normalizedValue = URI(scheme, parsed.rawAuthority, path, parsed.rawQuery, null).toASCIIString()
  val key = buildString {
    append(scheme)
    append("://")
    append(authority.lowercase())
    append(path)
    parsed.rawQuery?.let { append('?').append(it) }
  }
  ValidatedFeedUrl(normalizedValue, key)
}.getOrNull()

private fun secureDocumentBuilderFactory(): DocumentBuilderFactory =
  DocumentBuilderFactory.newInstance().apply {
    isNamespaceAware = false
    isExpandEntityReferences = false
    runCatching { isXIncludeAware = false }
    runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
    runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
    runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
    runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
    runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") }
    runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "") }
  }

private fun Element.childElements(): List<Element> = buildList {
  val nodes = childNodes
  for (index in 0 until nodes.length) {
    (nodes.item(index) as? Element)?.let(::add)
  }
}

private fun Element.attributeIgnoreCase(name: String): String {
  val values = attributes ?: return ""
  for (index in 0 until values.length) {
    val attribute = values.item(index)
    if (attribute.nodeName.equals(name, ignoreCase = true)) return attribute.nodeValue.orEmpty()
  }
  return ""
}
