package dev.terashima.yomitorirss.feature.youtube.data

import java.io.ByteArrayInputStream
import java.time.Instant
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

internal data class ParsedYouTubeFeed(
  val channelId: String,
  val channelTitle: String,
  val videos: List<ParsedYouTubeVideo>,
)

internal data class ParsedYouTubeVideo(
  val id: String,
  val channelId: String,
  val title: String,
  val url: String,
  val publishedAtEpochMillis: Long,
)

internal class YouTubeFeedParser {
  fun parse(bytes: ByteArray): ParsedYouTubeFeed {
    val factory = DocumentBuilderFactory.newInstance().apply {
      isNamespaceAware = true
      runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
      runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
      runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
      // XInclude is false by default. Android's JAXP implementation can throw even when setting it to false.
      isExpandEntityReferences = false
    }
    val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    val root = document.documentElement ?: error("YouTubeフィードが空です")
    val channelId = root.firstText(YT_NAMESPACE, "channelId")
      ?.let(::canonicalChannelId)
      ?: error("YouTubeフィードにchannelIdがありません")
    val channelTitle = root.firstDirectText(ATOM_NAMESPACE, "title")
      ?: error("YouTubeフィードにチャンネル名がありません")
    val entries = root.getElementsByTagNameNS(ATOM_NAMESPACE, "entry")
    val videos = buildList {
      for (index in 0 until entries.length) {
        val entry = entries.item(index) as? Element ?: continue
        val id = entry.firstText(YT_NAMESPACE, "videoId") ?: continue
        val entryChannelId = entry.firstText(YT_NAMESPACE, "channelId")
          ?.let(::canonicalChannelId)
          ?: channelId
        val title = entry.firstDirectText(ATOM_NAMESPACE, "title") ?: continue
        val url = entry.alternateLink() ?: "https://www.youtube.com/watch?v=$id"
        val published = entry.firstDirectText(ATOM_NAMESPACE, "published")
          ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
          ?: continue
        add(
          ParsedYouTubeVideo(
            id = id,
            channelId = entryChannelId,
            title = title,
            url = url,
            publishedAtEpochMillis = published,
          ),
        )
      }
    }
    return ParsedYouTubeFeed(channelId, channelTitle, videos)
  }

  private fun canonicalChannelId(value: String): String = when {
    CHANNEL_ID_PATTERN.matches(value) -> value
    CHANNEL_ID_WITHOUT_PREFIX_PATTERN.matches(value) -> "UC$value"
    else -> error("YouTubeフィードのchannelIdが不正です")
  }

  private fun Element.firstText(namespace: String, localName: String): String? =
    getElementsByTagNameNS(namespace, localName)
      .item(0)
      ?.textContent
      ?.trim()
      ?.takeIf(String::isNotBlank)

  private fun Element.firstDirectText(namespace: String, localName: String): String? {
    val children = childNodes
    for (index in 0 until children.length) {
      val element = children.item(index) as? Element ?: continue
      if (element.namespaceURI == namespace && element.localName == localName) {
        return element.textContent?.trim()?.takeIf(String::isNotBlank)
      }
    }
    return null
  }

  private fun Element.alternateLink(): String? {
    val links = getElementsByTagNameNS(ATOM_NAMESPACE, "link")
    for (index in 0 until links.length) {
      val link = links.item(index) as? Element ?: continue
      if (link.getAttribute("rel") == "alternate") {
        return link.getAttribute("href").trim().takeIf(String::isNotBlank)
      }
    }
    return null
  }

  private companion object {
    const val ATOM_NAMESPACE = "http://www.w3.org/2005/Atom"
    const val YT_NAMESPACE = "http://www.youtube.com/xml/schemas/2015"
    val CHANNEL_ID_PATTERN = Regex("^UC[A-Za-z0-9_-]{22}$")
    val CHANNEL_ID_WITHOUT_PREFIX_PATTERN = Regex("^[A-Za-z0-9_-]{22}$")
  }
}
